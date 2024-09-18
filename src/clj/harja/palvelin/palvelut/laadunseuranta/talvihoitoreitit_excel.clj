(ns harja.palvelin.palvelut.laadunseuranta.talvihoitoreitit-excel
  "Luetaan talvihoitoreitit excelistä tiedot ulos"
  (:require [clojure.string :as str]
            [dk.ative.docjure.spreadsheet :as xls]
            [slingshot.slingshot :refer [throw+]]
            [harja.kyselyt.konversio :as konversio]))


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
               (and
                 ;; Annetaan hieman vapauksia kenttien nimille
                 (or
                   (= "Reitin nimi*" (first rivi))
                   (= "Reitin nimi" (first rivi))
                   (= "Reitti" (first rivi))))
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
                 (or
                   (= "Reitin nimi*" (first rivi))
                   (= "Reitin nimi" (first rivi))
                   (= "Reitti" (first rivi)))
                 (or (= "Tienro*" (second rivi))
                   (= "Tie" (second rivi))
                   (= "Tienro" (second rivi))))
               idx))
           data)))

(defn reitit-excelista [data otsikkotiedot]
  (keep
    ;; Poistetaan rivi kokonaan, mikäli nimikenttä on nil. Eli oletetaan että rivillä ei ole
    ;; annettu muutenkaan mitään asiaan liittyvää tietoa vaan rivi liittyy otsikointiin tms.
    (fn [rivi]
      (if (nil? (second rivi))
        nil
        {:nimi (nth rivi 0)
         :tie (konversio/konvertoi->int (nth rivi 1))
         :aosa (konversio/konvertoi->int (nth rivi 2))
         :aet (konversio/konvertoi->int (nth rivi 3))
         :losa (konversio/konvertoi->int (nth rivi 4))
         :let (konversio/konvertoi->int (nth rivi 5))
         :hoitoluokka (nth rivi 6)
         :pituus (konversio/konvertoi->int (nth rivi 7))}))
    (subvec data (inc otsikkotiedot))))

(defn reitit-ja-kalusto-excelista [data otsikkotiedot]
  (keep
    ;; Poistetaan rivi kokonaan, mikäli nimikenttä on nil. Eli oletetaan että rivillä ei ole
    ;; annettu muutenkaan mitään asiaan liittyvää tietoa vaan rivi liittyy otsikointiin tms.
    (fn [rivi]
      (if (nil? (first rivi))
        nil
        {:nimi (nth rivi 0)
         :tr (nth rivi 1) ;; Traktori
         :ka (nth rivi 2) ;; Kuorma-auto
         :kup (nth rivi 3) ;; Kup
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

        ;; Haetaan data excelistä rivi-indeksin perusteella
        kalusto-rivit (reitit-ja-kalusto-excelista raaka-data-nimet-ja-kalusto kalusto-alkuindeksi)
        reitti-rivit (reitit-excelista raaka-data-reitit reitti-alkuindeksi)

        ;; Koska saman nimiselle reitille voi tulla useita tieosoitteita, niin groupataan reitit nimen perusteella
        reitti-rivit (group-by :nimi reitti-rivit)
        kalusto-rivit (group-by :nimi kalusto-rivit)

        reittien-nimet (keys reitti-rivit)
        reitti-rivi-kpl (reduce (fn [acc reitin-nimi]
                                  (+ acc (count (get-in reitti-rivit [reitin-nimi]))))
                          0 reittien-nimet)
        kalusto-rivi-kpl (reduce (fn [acc reitin-nimi]
                                   (+ acc (count (get-in kalusto-rivit [reitin-nimi]))))
                           0 reittien-nimet)

        ;; Reitti ja kalusto tabeilla täytyy olla sama määrä rivejä
        _ (when (not= kalusto-rivi-kpl reitti-rivi-kpl)
            (throw+ {:type :validaatiovirhe
                     :virheet [{:virheet (str "Reittien ja kaluston määrä ei täsmää. Reittien määrä: " reitti-rivi-kpl
                                           ", kaluston määrä: " kalusto-rivi-kpl)}]}))

        ;; Mäpätään reitit ja kalusto yhteen
        reitit (reduce
                 (fn [tulos nimi]
                   (let [;; Excelistä tuotavalla talvihoitoreitillä ei ole tunnistetta tai ulkoista id:tä, joten käytetään nimeä
                         talvihoitoreitti {:reittinimi nimi
                                           :tunniste nimi
                                           :sijainnit (map #(dissoc % :nimi) (get-in reitti-rivit [nimi]))}
                         kalustot (mapv #(dissoc % :nimi) (get-in kalusto-rivit [nimi]))

                         lopulliset-kalustot (mapv #(jaa-mappi-helpperi %) kalustot)
                         reitit (map-indexed (fn [indeksi reitti]
                                               (assoc reitti :kalustot (nth lopulliset-kalustot indeksi)))
                                  (:sijainnit talvihoitoreitti))

                         lopullinen-talvihoitoreitti (assoc talvihoitoreitti :sijainnit reitit)]
                     (conj tulos lopullinen-talvihoitoreitti)))
                 [] reittien-nimet)]
    reitit))
