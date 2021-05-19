(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-excel
  "Luetaan paikkauskohteet excelistä tiedot ulos"
  (:require [dk.ative.docjure.spreadsheet :as xls]
            [clojure.set :as set]
            [clojure.string :refer [trim]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.kyselyt.paikkaus :as q]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.pvm :as pvm]
            [harja.domain.paikkaus :as paikkaus])
  (:import (org.apache.poi.ss.util CellRangeAddress)
           (java.util Date)))

(defn- kasittele-tyomenetelma [menetelma]
  ;; Käsittele työmenetelmä vain, jos se on olemassa
  (when menetelma
    (paikkaus/lyhenna-tyomenetelma (trim menetelma))))

(defn erottele-paikkauskohteet [workbook]
  (let [sivu (first (xls/sheet-seq workbook)) ;; Käsitellään excelin ensimmäinen sivu tai tabi
        ;; Esimerkki excelissä paikkauskohteet alkavat vasta viidenneltä riviltä.
        ;; Me emme voi olla tästä kuitenkaan ihan varmoja, niin luetaan varalta kaikki data excelistä ulos
        raaka-data (->> sivu
                        xls/row-seq
                        (remove nil?)
                        (map xls/cell-seq)
                        (mapv
                          (fn [rivi]
                            (map-indexed (fn [indeksi arvo]
                                           (if (or
                                                 (= indeksi 8)
                                                 (= indeksi 9))
                                             (try
                                               (.getDateCellValue arvo)
                                               (catch Exception e
                                                 ;(println "Saatiin virhe päivämäärän luvusta, ei välitetä" (pr-str e))
                                                 (xls/read-cell arvo)))
                                             (xls/read-cell arvo)))
                                         rivi))))

        ;; Tämä toimii nykyisellä excel-pohjalla toistaiseksi.
        ;; Katsotaan, millä rivillä otsikkorivi on, oletuksena että sieltä löytyy ainakin "Nro." ja "kohde" otsikot.
        ;; Ja otetaan otsikon jälkeiset rivit, joissa on nimi. Päästetään tässä vaiheessa myös selvästi virheelliset
        ;; rivit läpi, jotta voidaan palauttaa validaatiovirheet.
        otsikko-idx (first (keep-indexed
                             (fn [idx rivi]
                               (when
                                 (and
                                   ;; Annetaan hieman vapauksia kenttien nimille
                                   (or (= "Nro." (first rivi))
                                       (= "Nro" (first rivi)))
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
                         :tyomenetelma (kasittele-tyomenetelma (nth rivi 10))
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

(defn tee-excel [tiedostonimi data]
  (let [wb (xls/create-workbook "Paikkaukset"
                                [["Paikkausehdotukset"]
                                 []
                                 []
                                 paikkauskohteet-otsikot]
                                "Lähtötiedot"
                                lahtotiedot-sisalto
                                )
        ps (xls/select-sheet "Paikkaukset" wb)
        _ (do
            (doall (map #(.autoSizeColumn ps %) (range 16)))
            (xls/set-cell-style! (xls/select-cell "A1" ps)
                                 (xls/create-cell-style! wb {:font {:size 14 :name "Arial" :bold true}
                                                             :valign :center}))
            (xls/set-row-style! (nth (xls/row-seq ps) 3) (xls/create-cell-style! wb {:font {:name "Arial" :bold true :size 10}
                                                                                     :border-bottom :thin
                                                                                     :border-top :thin
                                                                                     :border-left :thin
                                                                                     :border-right :thin})))
        file (xls/save-workbook-into-file! tiedostonimi wb)]
    file))

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

(defn vie-paikkauskohteet-exceliin
  [db workbook user tiedot]
  (let [urakka (first (q-urakat/hae-urakka db (:urakka-id tiedot)))
        kohteet (q/paikkauskohteet db user tiedot)
        kohteet (keep
                  (fn [kohde]
                    (if (oikeudet/voi-lukea? oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (:urakka-id kohde) user)
                      kohde
                      nil)) ;; Poistetaan ne kohteet, joihin käyttäjällä ei ole oikeutta
                  kohteet)
        ;; Muokkaa työmenetelmä tekstimuotoon
        kohteet (mapv (fn [k]
                        (update k :tyomenetelma #(paikkaus/kuvaile-tyomenetelma %))) kohteet)
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

        rivit (rivita-kohteet kohteet)
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
                               :orientaatio :landscape}]
                   (if (empty? taulukot)
                     [[:taulukko optiot nil [["Ei paikkauskohteita"]]]]
                     taulukot))]
    (excel/muodosta-excel (vec taulukko)
                          workbook)))
