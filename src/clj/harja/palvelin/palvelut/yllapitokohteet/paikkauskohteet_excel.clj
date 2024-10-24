(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-excel
  "Luetaan paikkauskohteet excelistä tiedot ulos"
  (:require [clojure.string :as str]
            [dk.ative.docjure.spreadsheet :as xls]
            [slingshot.slingshot :refer [throw+]]
            [clojure.string :refer [trim]]
            [harja.domain.oikeudet :as oikeudet]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.kyselyt.paikkaus :as q]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.pvm :as pvm]
            [harja.domain.paikkaus :as paikkaus]))


(defn- lue-excel-raaka-data [sivu pvm-sarakkeet]
  (->> sivu
    xls/row-seq
    (remove nil?)
    (map xls/cell-seq)
    (mapv
      (fn [rivi]
        (map-indexed (fn [indeksi arvo]
                       (if (contains? pvm-sarakkeet indeksi)
                         (try
                           (.getDateCellValue arvo)
                           (catch Exception e
                             ;(println "Saatiin virhe päivämäärän luvusta, ei välitetä" (pr-str e))
                             (xls/read-cell arvo)))
                         (xls/read-cell arvo)))
          rivi)))))


(defn- validoi-reikapaikkaus-rivit
  "  Validoidaan reikäpaikkaus excel-rivit
   - Älä salli nil- arvoja
   - Tunnisteen pitää olla unique
   - Palauta virhe ja kerro millä rivillä ja mitä meni pieleen"
  [rivit]
  (let [nahdyt-tunnisteet (atom #{})]
    ;; Palauta tulokset mäpättynä vectoriin [{}] joihin lisätty :virhe mikäli virheitä on
    (vec
      (map-indexed (fn [rivi-nro rivi]
                     (let [;; Paikkaukset alkavat rivistä 4
                           rivi-nro (+ rivi-nro 4)
                           nil-avaimet (vec
                                         ;; Katso onko rivillä tyhjiä sarakkeita
                                         (keep #(when (nil? (% rivi))
                                                  ;; Näytetään tyhjät sarakkeet muodossa ["kenttä"]
                                                  (name %))
                                           (keys rivi)))
                           ; Validoi kokonaisluvut 
                           kokonaislukuja? (every? #(integer? (% rivi))
                                             [:aosa :losa :tie :aet :let :tunniste])
                           ; Validoi desimaalit
                           kustannus-maara-validi? (every?
                                                     ;; Saa olla joko int tai float 
                                                     #(or
                                                        (integer? (% rivi))
                                                        (float? (% rivi)))
                                                     [:kustannus :maara])


                           tunniste (:tunniste rivi)
                           ;; Onko tämä tunniste jo nähty?
                           tunniste-olemassa? (and tunniste (contains? @nahdyt-tunnisteet tunniste))]
                       ;; Lisää nähty tunniste atomiin
                       (when tunniste (swap! nahdyt-tunnisteet conj tunniste))
                       (cond
                         (not-empty nil-avaimet)
                         (assoc rivi :virhe (str "Rivillä " rivi-nro " on tyhjiä kenttiä: " nil-avaimet))

                         (not kokonaislukuja?)
                         (assoc rivi :virhe (str "Rivillä " rivi-nro " aosa, losa, tie, aet, let, sekä tunniste pitää olla kokonaislukuja."))

                         (not kustannus-maara-validi?)
                         (assoc rivi :virhe (str "Rivillä " rivi-nro " kustannus ja määrä pitää olla joko kokonaisluku tai desimaaliluku."))

                         tunniste-olemassa?
                         (assoc rivi :virhe (str "Rivillä " rivi-nro " syötetty tunniste on jo olemassa: " tunniste))

                         :else rivi)))
        rivit))))


(defn parsi-syotetyt-reikapaikkaukset [workbook]
  (let [pvm-sarakkeet #{1} ;; Sarakkeet jotka ovat PVM muodossa
        sivu (first (xls/sheet-seq workbook))
        raaka-data (lue-excel-raaka-data sivu pvm-sarakkeet)
        ;; Syötetty data mistä leikattu kaikki otsikot sun muut 
        ;; Tämä siis leikkaa kaikki aikaisemmat rivit siihen asti kun "Tunniste*" löytyy, joka on otsikkorivi, sen jälkeen tulee oikea data
        syotetty-data (->> raaka-data
                        (drop-while #(not (some (fn [data] (= "Tunniste*" data)) %1)))
                        rest
                        (remove #(every? nil? %))) ;; Poista kaikki täysin tyhjät rivit myös
        ;; Jostain syystä numerot tulee aina floattina, muunnetaan nämä kokonaisnumeroiksi
        kokonaisluvut #{:tie :aosa :aet :losa :let :tunniste}
        ;; Sarakkeet avaimina, eli excelin otsikot
        sarakkeet [:tunniste :pvm :tie :aosa :aet :losa :let :menetelma :maara :yksikko :kustannus]
        ;; Mäppää sekvenssi vectoriin, jonka sisällä on mappeja, eli (() ()) -> [{} {}]
        syotetty-data (mapv (fn [sisempi-sekvenssi]
                              (let [konvertoitu (map-indexed (fn [indeksi arvo]
                                                               (if (and
                                                                     (float? arvo)
                                                                     (contains? kokonaisluvut (nth sarakkeet indeksi)))
                                                                 (int arvo)
                                                                 arvo))
                                                  sisempi-sekvenssi)]
                                (zipmap sarakkeet konvertoitu)))
                        syotetty-data)
        validoitu-data (validoi-reikapaikkaus-rivit syotetty-data)]
    validoitu-data))


(defn erottele-paikkauskohteet [workbook]
  (let [pvm-sarakkeet #{8 9} ;; Sarakkeet jotka ovat PVM muodossa
        sivu (first (xls/sheet-seq workbook)) ;; Käsitellään excelin ensimmäinen sivu tai tabi
        ;; Esimerkki excelissä paikkauskohteet alkavat vasta kuudennelta riviltä.
        ;; Me emme voi olla tästä kuitenkaan ihan varmoja, niin luetaan varalta kaikki data excelistä ulos
        raaka-data (lue-excel-raaka-data sivu pvm-sarakkeet)

        ;; Tämä toimii nykyisellä excel-pohjalla toistaiseksi.
        ;; Katsotaan, millä rivillä otsikkorivi on, oletuksena että sieltä löytyy ainakin "Nro." ja "kohde" otsikot.
        ;; Ja otetaan otsikon jälkeiset rivit, joissa on nimi. Päästetään tässä vaiheessa myös selvästi virheelliset
        ;; rivit läpi, jotta voidaan palauttaa validaatiovirheet.
        otsikko-idx (first (keep-indexed
                             (fn [idx rivi]
                               (when
                                 (and
                                   ;; Annetaan hieman vapauksia kenttien nimille
                                   (or
                                     (= "Nro." (first rivi))
                                     (= "Nro" (first rivi))
                                     (= "Nro *" (first rivi))
                                     (= "Nro. *" (first rivi)))
                                   (or (= "Kohde" (second rivi))
                                     (= "Kohteen nimi *" (second rivi))))
                                 idx))
                             raaka-data))
        kohteet (keep
                  ;; Poistetaan rivi kokonaan, mikäli nimikenttä (Kohteen nimi) on nil. Eli oletetaan että rivillä ei ole
                  ;; annettu muutenkaan paikkauskohteisiin liittyvää tietoa vaan rivi liittyy otsikointiin tms.
                  (fn [rivi]
                    (if (nil? (second rivi))
                      nil
                      (let [alkupvm (nth rivi 8)
                            loppupvm (nth rivi 9)]
                        {:ulkoinen-id (nth rivi 0)
                         :nimi (nth rivi 1)
                         :tie (nth rivi 2)
                         :ajorata (nth rivi 3)
                         :aosa (nth rivi 4)
                         :aet (nth rivi 5)
                         :losa (nth rivi 6)
                         :let (nth rivi 7)
                         :alkupvm (or (pvm/->pvm alkupvm) alkupvm)
                         :loppupvm (or (pvm/->pvm loppupvm) loppupvm)
                         :tyomenetelma (when (nth rivi 10) (trim (nth rivi 10))) ;; Excelissä voi olla turhia välilyöntejä
                         :suunniteltu-maara (nth rivi 11)
                         :yksikko (nth rivi 12)
                         :suunniteltu-hinta (nth rivi 13)
                         :lisatiedot (nth rivi 14)})))
                  (subvec raaka-data (inc otsikko-idx)))]
    kohteet))

(def lahtotiedot-sisalto
  [["Paikkausmenetelmät" "Yksikkö"]
   ["AB-paikkaus levittäjällä" "t"]
   ["PAB-paikkaus levittäjällä" "m2"]
   ["KT-valuasfalttipaikkaus (KTVA) " "jm"]
   ["Konetiivistetty reikävaluasfalttipaikkaus (REPA)" "kpl"]
   ["Sirotepuhalluspaikkaus (SIPU)" ""]
   ["Sirotepintauksena tehty lappupaikkaus (SIPA)" ""]
   ["Urapaikkaus (UREM/RREM)" ""]
   ["Kannukaatosaumaus" ""]
   ["KT-valuasfalttisaumaus" ""]
   ["Avarrussaumaus" ""]
   ["Sillan kannen päällysteen päätysauman korjaukset" ""]
   ["Reunapalkin ja päällysteen välisen sauman tiivistäminen" ""]
   ["Reunapalkin liikuntasauman tiivistäminen" ""]
   ["Käsin tehtävät paikkaukset pikapaikkausmassalla" ""]
   ["AB-paikkaus käsin" ""]
   ["PAB-paikkaus käsin" ""]
   ["Muu päällysteiden paikkaustyö" ""]])

(def paikkauskohteet-otsikot
  ["Nro." "Kohde" "Tienro" "Ajr" "Aosa"
   "Aet" "Losa" "Let" "Arvioitu aloitus pvm"
   "Arvioitu lopetus pvm" "Työmenetelmä" "Määrä"
   "Yksikkö" "Kustannusarvio" "Lisätiedot"])

(defn- rivita-kohteet [kohteet]
  (concat
    (when (> (count kohteet) 0)
      (mapcat
        (fn [rivi]
          (let [suunniteltu-summa (or (:suunniteltu-hinta rivi) 0)]
            [{:rivi [(str (:ulkoinen-id rivi))
                     (:nimi rivi)
                     (:tie rivi)
                     (:ajorata rivi)
                     (:aosa rivi)
                     (:aet rivi)
                     (:losa rivi)
                     (:let rivi)
                     (pvm/pvm-opt (:alkupvm rivi))
                     (pvm/pvm-opt (:loppupvm rivi))
                     (:tyomenetelma rivi)
                     (:suunniteltu-maara rivi)
                     (:yksikko rivi)
                     suunniteltu-summa
                     (:lisatiedot rivi)]
              :lihavoi? false}]))
        kohteet))))

(defn muodosta-excelrivit [kohteet]
  (let [kohteet (rivita-kohteet kohteet)
        ensimmainen-rivi-jossa-kustannuksia 5
        yhteenvetorivi [[nil nil nil nil nil nil nil nil nil nil nil nil
                         "Yhteensä:" [:kaava {:kaava :summaa-yllaolevat
                                              :alkurivi ensimmainen-rivi-jossa-kustannuksia
                                              :loppurivi (+ (count kohteet)
                                                           (- ensimmainen-rivi-jossa-kustannuksia 1))}]
                         nil]]
        ohjerivi [{:lihavoi? true
                   :rivi [nil "Älä käytä tätä exceliä kohteiden muokkaukseen. Muokkaa kohteet käyttöliittymässä."
                          nil nil nil nil nil nil nil nil nil nil nil nil nil]}]]
    (concat
      (when (> (count kohteet) 0)
        kohteet)
      yhteenvetorivi
      ohjerivi)))

(defn vie-paikkauskohteet-exceliin
  [db workbook user tiedot]
  (let [urakka (first (q-urakat/hae-urakka db (:urakka-id tiedot)))
        tyomenetelmat (q/hae-paikkauskohteiden-tyomenetelmat db)
        kohteet (q/paikkauskohteet db user tiedot)
        kohteet (keep
                  (fn [kohde]
                    (if (oikeudet/voi-lukea? oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (:urakka-id kohde) user)
                      kohde
                      nil)) ;; Poistetaan ne kohteet, joihin käyttäjällä ei ole oikeutta
                  kohteet)
        ;; Muokkaa työmenetelmä tekstimuotoon
        kohteet (mapv (fn [k]
                        (update k :tyomenetelma #(paikkaus/tyomenetelma-id->nimi % tyomenetelmat))) kohteet)
        sarakkeet [{:otsikko "Nro." :tasaa :oikea}
                   {:otsikko "Kohde"}
                   {:otsikko "Tienro"}
                   {:otsikko "Ajr"}
                   {:otsikko "Aosa"}
                   {:otsikko "Aet"}
                   {:otsikko "Losa"}
                   {:otsikko "Let"}
                   {:otsikko "Arvioitu aloitus pvm" :tasaa :oikea}
                   {:otsikko "Arvioitu lopetus pvm" :tasaa :oikea}
                   {:otsikko "Työmenetelmä"}
                   {:otsikko "Määrä"}
                   {:otsikko "Yksikkö" :tasaa :oikea}
                   {:otsikko "Kustannusarvio" :tasaa :oikea}
                   {:otsikko "Lisätiedot"}]

        rivit (muodosta-excelrivit kohteet)
        optiot {:nimi "Paikkauskohteet"
                :sheet-nimi "Paikkauskohteet"
                :tyhja (if (empty? kohteet) "Ei paikkauskohteita.")
                :lista-tyyli? true
                :rivi-ennen [{:teksti "Paikkauskohteet" :sarakkeita 2}
                             {:teksti (str (pvm/pvm-opt (:alkupvm tiedot)) " - " (pvm/pvm-opt (:loppupvm tiedot))) :sarakkeita 7}]}
        taulukot [[:taulukko optiot sarakkeet
                   rivit]]
        tiedostonimi (str (:nimi urakka) "-Paikkauskohteet-" (pvm/vuosi (:alkupvm tiedot)))
        ;; Nimi: <urakkan_nimi>-Paikkauskohteet-<vuosi>
        taulukko (concat
                   [:raportti {:nimi tiedostonimi
                               :raportin-yleiset-tiedot {:raportin-nimi "Paikkauskohteet"
                                                         :urakka (:nimi urakka)
                                                         :alkupvm (pvm/kokovuosi-ja-kuukausi (:alkupvm tiedot))
                                                         :loppupvm (pvm/kokovuosi-ja-kuukausi (:loppupvm tiedot))}
                               :orientaatio :landscape}]
                   (if (empty? taulukot)
                     [[:taulukko optiot nil [["Ei paikkauskohteita"]]]]
                     taulukot))]
    (excel/muodosta-excel (vec taulukko)
      workbook)))

(defn- lue-urem-excelin-otsikot [sivu]
  (try
    (first
      (->> sivu
        xls/row-seq
        (drop 6)
        (take 7) ;; millä rivillä otsikot ovat
        (map xls/cell-seq)
        (map #(take 16 %)) ;; 16 saraketta
        (map (partial map xls/read-cell))))
    (catch Exception e
      (log/error e "Vääränlainen Excel-pohja UREM-tuonnissa: "))))

(defn- lue-urem-kokonaismassamaara [sivu]
  (try
    (ffirst
      (->> sivu
        xls/row-seq
        ;; hypätään riville, missä kokonaismassamäärä syötetään
        (drop 3)
        (map xls/cell-seq)
        (map #(take 1 %)) ;; luetaan vain eka sarake
        (map (partial map xls/read-cell))))
    (catch Exception e
      (log/error e "Ei löytynyt kokonaismassamäärää Excel-pohjasta"))))

(def urem-excel-pohjan-otsikot
  (-> "public/excel/harja_urapaikkaustoteumien_tuonti_pohja.xlsx"
    xls/load-workbook-from-resource
    xls/sheet-seq
    first
    lue-urem-excelin-otsikot))

(defn erottele-uremit [workbook]
  (let [sivu (first (xls/sheet-seq workbook))
        excelin-otsikot-tasmaavat-pohjaan? (= (lue-urem-excelin-otsikot sivu)
                                             urem-excel-pohjan-otsikot)
        urem-kok-massamaara (lue-urem-kokonaismassamaara sivu)
        paikkaukset (when excelin-otsikot-tasmaavat-pohjaan?
                      (->> sivu
                        xls/row-seq
                        ;; Toisin kuin paikkauskohteissa, oletetaan että käyttäjä käyttää meidän pohjaa.
                        ;; Pudotetaan otsikkorivit.
                        (drop 7)
                        (map xls/cell-seq)
                        (mapv
                          (fn [rivi]
                            ;; Ei lueta rivejä sarakkeita 17. (Q) jälkeen.
                            (let [rivi (take 17 rivi)]
                              {:rivi (inc (.getRowIndex (first rivi)))
                               :paikkaus (map-indexed (fn [indeksi arvo]
                                                        (if (or
                                                              (= indeksi 0)
                                                              (= indeksi 1))
                                                          (try
                                                            ;; Yritetään lukea päivämääräkentät 1. ja 2. sarakkeista
                                                            (xls/read-cell-value arvo true)
                                                            (catch Exception e
                                                              ;; Jos ei onnistu, ei haittaa, validoidaan myöhemmin.
                                                              (xls/read-cell arvo)))
                                                          (xls/read-cell arvo)))
                                           rivi)})))
                        ;; Pohjassa on alustettu useampi sata riviä. Pudotetaan nekin pois.
                        (filter #(not (every? nil? (:paikkaus %))))))]
    {:paikkaukset paikkaukset
     :urem-kok-massamaara urem-kok-massamaara
     :virhe (when-not excelin-otsikot-tasmaavat-pohjaan? "Excelin otsikot eivät täsmää pohjaan")}))

(defn vie-reikapaikkaukset-exceliin
  [db hae-reikapaikkaukset workbook user {:keys [urakka-id] :as tiedot}]
  (let [reikapaikkaukset (hae-reikapaikkaukset db user tiedot)
        urakka (:nimi (first (q-urakat/hae-urakan-nimi db urakka-id)))
        yhteensa-rivi [[nil nil nil nil nil nil nil nil nil nil (apply + (map :kustannus reikapaikkaukset))]]
        reikapaikkaukset (mapv
                           (fn [{:keys [tunniste alkuaika tie aosa aet losa let tyomenetelma-nimi maara reikapaikkaus-yksikko kustannus]}]
                             [tunniste
                              (pvm/pvm-opt alkuaika)
                              tie
                              aosa
                              aet
                              losa
                              let
                              tyomenetelma-nimi
                              maara
                              reikapaikkaus-yksikko
                              kustannus])
                           reikapaikkaukset)
        tyhja-rivi [[]]
        excel-nimi (str urakka " reikäpaikkaukset")
        taulukko (vec (concat
                        [:pohjan-taytto {:nimi excel-nimi
                                         :ensimmainen-rivi 1
                                         :sheet-nro 0}]
                        [(vec (concat yhteensa-rivi tyhja-rivi reikapaikkaukset))]))]
    (excel/muodosta-excel taulukko
      workbook)))
