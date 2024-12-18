(ns harja.palvelin.palvelut.laadunseuranta.talvihoitoreitit-excel
  "Luetaan talvihoitoreitit excelistä tiedot ulos"
  (:require [clojure.string :as str]
            [dk.ative.docjure.spreadsheet :as xls]
            [harja.kyselyt.talvihoitoreitit :as talvihoitoreitit-q]
            [slingshot.slingshot :refer [throw+]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.raportointi.excel :as excel]))

(defn- nimea-hoitoluokka-mahdollisesti
  "Excelistä saadaan hoitoluokka, joka on kirjallisessa muodossa helposti luettavissa, kun käsitellään
  Huoltoaukkoja ja pysäköintialueita. Tällaista helposti luettavaa muotoa ei ole Harjan hoitoluokka-taulukossa.
  Nimetään siis nämä muutama hoitoluokka Harjan ymmärtämään muotoon."
  [excel-hoitoluokka]
  (case excel-hoitoluokka
    "Huoltoaukot ja pysäköintialueet - Talvihoito" "Talvihoito"
    "Huoltoaukot ja pysäköintialueet - Talvihoito osin" "Hoito osin"
    "Huoltoaukot ja pysäköintialueet - Ei talvihoitoa" "Ei talvihoitoa"
    excel-hoitoluokka))

(defn- lue-excel-raaka-data [sivu]
  (->> sivu
    xls/row-seq
    (remove nil?)
    (map xls/cell-seq)
    (mapv
      (fn [rivi]
        (map-indexed (fn [indeksi arvo]
                       (xls/read-cell arvo))
          rivi)))))

(defn reitin-nimi-ja-kalusto-alkuindeksi [data]
  ;; Katsotaan, millä rivillä otsikkorivi on. Oletuksena että sieltä löytyy ainakin "Reitin nimi" ja "TR (kpl)" otsikot.
  ;; Me tarvitaan data eli otsikkorivin jälkeiset rivit. Päästetään tässä vaiheessa myös selvästi virheelliset
  ;; rivit läpi, jotta voidaan palauttaa validaatiovirheet.
  (first (keep-indexed
           (fn [idx rivi]
             (when
               (boolean (#{"Reitin nimi*" "Reitin nimi" "Reitti"} (first rivi)))
               idx))
           data)))

(defn reitit-alkuindeksi [data]
  ;; Katsotaan, millä rivillä otsikkorivi on, oletuksena että sieltä löytyy ainakin "Reitin nimi*" ja "Tienro*" otsikot.
  ;; Ja otetaan otsikon jälkeiset rivit, joissa on nimi. Päästetään tässä vaiheessa myös selvästi virheelliset
  ;; rivit läpi, jotta voidaan palauttaa validaatiovirheet.
  (first (keep-indexed
           (fn [idx rivi]
             (when
               (and
                 ;; Annetaan hieman vapauksia kenttien nimille
                 (boolean (#{"Reitin nimi*" "Reitin nimi" "Reitti"} (first rivi)))
                 (boolean (#{"Tienro*" "Tie" "Tienro"} (second rivi))))
               idx))
           data)))

(defn- kasittele-reitin-pituus [pituus km-kaytossa?]
  (let [pituus (if (= "java.lang.String" (type pituus))
                 (Float/parseFloat pituus)
                 pituus)]

    (if km-kaytossa?
      (* 1000 pituus)
      pituus)))

(defn reitit-excelista [data otsikkotiedot km-kaytossa?]
  (keep
    ;; Poistetaan rivi kokonaan, mikäli nimikenttä on nil. Eli oletetaan että rivillä ei ole
    ;; annettu muutenkaan mitään asiaan liittyvää tietoa vaan rivi liittyy otsikointiin tms.
    (fn [rivi]
      (when-not (nil? (second rivi))
        {:nimi (nth rivi 0)
         :tie (konversio/konvertoi->int (nth rivi 1))
         :aosa (konversio/konvertoi->int (nth rivi 2))
         :aet (konversio/konvertoi->int (nth rivi 3))
         :losa (konversio/konvertoi->int (nth rivi 4))
         :let (konversio/konvertoi->int (nth rivi 5))
         :hoitoluokka (nimea-hoitoluokka-mahdollisesti (nth rivi 6))
         :pituus (kasittele-reitin-pituus (nth rivi 7) km-kaytossa?)}))
    (subvec data (inc otsikkotiedot))))

(defn reitit-ja-kalusto-excelista [data otsikkotiedot]
  (keep
    ;; Poistetaan rivi kokonaan, mikäli nimikenttä on nil. Eli oletetaan että rivillä ei ole
    ;; annettu muutenkaan mitään asiaan liittyvää tietoa vaan rivi liittyy otsikointiin tms.
    (fn [rivi]
      (when-not (nil? (first rivi))
        {:nimi (nth rivi 0)
         :tr (when (> (count rivi) 1) (nth rivi 1)) ;; Traktori
         :ka (when (> (count rivi) 2) (nth rivi 2)) ;; Kuorma-auto
         :kup (when (> (count rivi) 3) (nth rivi 3)) ;; Kup
         }))
    (subvec data (inc otsikkotiedot))))

(defn jaa-mappi-helpperi [mappi]
  (keep (fn [[k v]] (when-not (nil? v)
                      {:kalustotyyppi (str/upper-case (name k)) :kalusto-lkm v})) mappi))

(defn lue-talvihoitoreitit-excelista [workbook]
  (let [nimet-ja-kalusto-sivu (first (xls/sheet-seq workbook))
        reitit-sivu (second (xls/sheet-seq workbook))
        ;; Esimerkki excelissä talvihoitoreitit alkavat vasta viidenneltä riviltä.
        ;; Me emme voi olla tästä kuitenkaan ihan varmoja, niin luetaan varalta kaikki data excelistä ulos
        raaka-data-nimet-ja-kalusto (lue-excel-raaka-data nimet-ja-kalusto-sivu)
        raaka-data-reitit (lue-excel-raaka-data reitit-sivu)

        kalusto-alkuindeksi (reitin-nimi-ja-kalusto-alkuindeksi raaka-data-nimet-ja-kalusto)
        reitti-alkuindeksi (reitit-alkuindeksi raaka-data-reitit)
        otsikot (into [] (nth raaka-data-reitit reitti-alkuindeksi))
        km-kaytossa? (boolean (some #{"Pituus (km)*"} otsikot))

        ;; Haetaan data excelistä rivi-indeksin perusteella
        kalusto-rivit (reitit-ja-kalusto-excelista raaka-data-nimet-ja-kalusto kalusto-alkuindeksi)
        reitti-rivit (reitit-excelista raaka-data-reitit reitti-alkuindeksi km-kaytossa?)

        ;; Koska saman nimiselle reitille voi tulla useita tieosoitteita, niin groupataan reitit nimen perusteella
        reitti-rivit (group-by :nimi reitti-rivit)
        kalusto-rivit (group-by :nimi kalusto-rivit)

        reittien-nimet (keys reitti-rivit)

        ;; Reitti ja kalusto tabeilla täytyy olla sama määrä rivejä
        _ (when (not= (count reitti-rivit) (count kalusto-rivit))
            (throw+ {:type :validaatiovirhe
                     :virheet [{:virheet (str "Reittien ja kaluston määrä ei täsmää. Reittien määrä: " (count reitti-rivit)
                                           ", kaluston määrä: " (count kalusto-rivit))}]}))

        ;; Mäpätään reitit ja kalusto yhteen
        reitit (reduce
                 (fn [tulos nimi]
                   (let [;; Excelistä tuotavalla talvihoitoreitillä ei ole tunnistetta tai ulkoista id:tä, joten käytetään nimeä
                         talvihoitoreitti {:reittinimi nimi
                                           :tunniste nimi
                                           :sijainnit (map #(dissoc % :nimi) (get-in reitti-rivit [nimi]))}
                         kalustot (mapv #(dissoc % :nimi) (get-in kalusto-rivit [nimi]))
                         lopulliset-kalustot (mapv #(jaa-mappi-helpperi %) kalustot)
                         reitit (map (fn [reitti] (assoc reitti :kalustot (flatten lopulliset-kalustot))) (:sijainnit talvihoitoreitti))
                         lopullinen-talvihoitoreitti (assoc talvihoitoreitti :sijainnit reitit)]
                     (conj tulos lopullinen-talvihoitoreitti)))
                 [] reittien-nimet)]
    reitit))

(defn- kalustomaara [avain kalustot]
  (let [tr-kalusto (filter #(= "TR" (:kalustotyyppi %)) kalustot)
        ka-kalusto (filter #(= "KA" (:kalustotyyppi %)) kalustot)
        kup-kalusto (filter #(= "KUP" (:kalustotyyppi %)) kalustot)
        kalusto (cond
                  (= :tr avain) (when-not (empty? tr-kalusto) (:kalustomaara (first tr-kalusto)))
                  (= :ka avain) (when-not (empty? ka-kalusto) (:kalustomaara (first ka-kalusto)))
                  (= :kup avain) (when-not (empty? kup-kalusto) (:kalustomaara (first kup-kalusto))))]
    kalusto))

(defn lataa-talvihoitoreitit-exceliin [db workbook user {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-talvihoitoreititys user urakka-id)
  (let [urakan-tiedot (first (harja.kyselyt.urakat/hae-urakka db {:id urakka-id}))
        urakan-talvihoitoreitit (talvihoitoreitit-q/hae-ja-muokkaa-talvihoitoreitit db urakka-id)
        kaluste-sarakkeet [{:otsikko "Reitin nimi" :lihavoitu? true}
                           {:otsikko "TR (kpl)" :lihavoitu? true}
                           {:otsikko "KA (kpl)" :lihavoitu? true}
                           {:otsikko "Kup (kpl)" :lihavoitu? true}]
        kalusto-optiot {:nimi "Talvihoitoreitit"
                        :otsikko "HARJA Talvihoitoreitit"
                        :sheet-nimi "Reittien nimet & kalusto"
                        :tyhja (if (empty? urakan-talvihoitoreitit) "Urakalla ei ole talvihoitoreittejä.")
                        :rivi-ennen [{:sarakkeita 1 :taustavari :WHITE}
                                     {:teksti "Kalusto"
                                      :sarakkeita 3
                                      :tummenna-teksti? true
                                      :tasaa :keskita
                                      :taustavari :GREY_25_PERCENT
                                      :lihavoitu? true}]}
        kalusto-rivit (mapv (fn [reitti]
                              {:rivi [(:nimi reitti)
                                      (kalustomaara :tr (:kalustot reitti))
                                      (kalustomaara :ka (:kalustot reitti))
                                      (kalustomaara :kup (:kalustot reitti))],
                               :lihavoi? false})
                        urakan-talvihoitoreitit)
        kalusto-taulukko [:taulukko kalusto-optiot kaluste-sarakkeet kalusto-rivit]
        reitti-optiot {:nimi "Talvihoitoreitit"
                       :sheet-nimi "Reittien tiedot"
                       :otsikko "HARJA Talvihoitoreitit"
                       :excel-alkutekstit ["Kaikki tiedot ovat pakollisia täyttää"
                                    "Reitin tulee sisältää kaikki siihen kuuluvat tiet (myös rampit, kiertoliittymät ja käpy-väylät). Ei hoitourakkaan kuulumattomia tietä, esim. katuja."
                                    "Reitti tunnistetaan nimen perusteella. Varmista, että kaikilla saman reitin tieosilla on sama nimi. Reitin nimet määritellään toisella välilehdellä."]

                       :tyhja (if (empty? urakan-talvihoitoreitit) "Urakalla ei ole talvihoitoreittejä.")}
        reitti-sarakkeet [{:otsikko "Reitin nimi*" :lihavoitu? true}
                          {:otsikko "Tienro*" :lihavoitu? true}
                          {:otsikko "Aosa*" :lihavoitu? true}
                          {:otsikko "Aet*" :lihavoitu? true}
                          {:otsikko "Losa*" :lihavoitu? true}
                          {:otsikko "Let*" :lihavoitu? true}
                          {:otsikko "Hoitoluokka*" :lihavoitu? true}
                          {:otsikko "Pituus (km)*" :lihavoitu? true}]
        reitti-rivit  (mapcat (fn [reitti]
                                (concat
                                  (mapv (fn [rivi]
                                          {:rivi [(:nimi reitti)
                                                  (:tie rivi)
                                                  (:alkuosa rivi)
                                                  (:alkuetaisyys rivi)
                                                  (:loppuosa rivi)
                                                  (:loppuetaisyys rivi)
                                                  (:hoitoluokka rivi)
                                                  (:pituus rivi)]})
                                    (:reitit reitti))))
                          urakan-talvihoitoreitit)
        reitti-taulukko [:taulukko reitti-optiot reitti-sarakkeet reitti-rivit]
        taulukot (conj []
                   [:otsikko "Otsikkoteksti"]
                   [:otsikko-heading "Reitti tunnistetaan nimen perusteella."]
                   kalusto-taulukko reitti-taulukko)
        raportti (concat
                   [:raportti {:nimi (str (:nimi urakan-tiedot) " - Talvihoitoreitit")
                               :raportin-yleiset-tiedot {:raportin-nimi (str (:nimi urakan-tiedot) "- Talvihoitoreitit")
                                                         :urakka (:nimi urakan-tiedot)}}]
                   (if (empty? taulukot)
                     [[:taulukko {} nil [["Ei kustannuksia valitulla aikavälillä"]]]]
                     taulukot))]
    (excel/muodosta-excel (vec raportti) workbook)))
