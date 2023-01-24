(ns harja.palvelin.palvelut.yllapitokohteet.paallystyskohteet-excel
  "Vie päällystyskohteiden kustannukset exceliin ja tuo samat tiedot Excelistä sisään."
  (:require [dk.ative.docjure.spreadsheet :as xls]
            [clojure.set :as set]
            [clojure.string :refer [trim] :as str]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.palvelin.palvelut.yllapitokohteet :as yllapitokohteet]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.pvm :as pvm]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [clojure.java.jdbc :as jdbc])
  (:import (org.apache.poi.ss.util CellRangeAddress)
           (java.util Date)))

(defn- validoi-excelin-otsikot
  "Vaadi oikeat otsikkotiedot määrämuotoisessa Excelissä"
  [[yhaid kohdenumero tunnus nimi
                                 sopimuksen-mukaiset-tyot maaramuutokset
                                 bitumi-indeksi kaasuindeksi kokonaishinta] ]
  (assert (= yhaid "Kohde-ID") "YHA ID:n otsikko oikein")
  (assert (= kohdenumero "Kohdenro") "Kohdenro otsikko oikein")
  (assert (= tunnus "Tunnus") "Tunnus otsikko oikein")
  (assert (= nimi "Nimi") "Nimi otsikko oikein")
  (assert (= sopimuksen-mukaiset-tyot "Tarjoushinta") "Tarjoushinta otsikko oikein")
  (assert (= maaramuutokset "Määrämuutokset") "Määrämuutokset otsikko oikein")
  (assert (= bitumi-indeksi "Sideaineen hintamuutokset") "Sideaineen hintamuutokset otsikko oikein")
  (assert (= kaasuindeksi "Nestekaasun ja kevyen polttoöljyn hintamuutokset") "Nestekaasun ja kevyen polttoöljyn hintamuutokset otsikko oikein")
  (assert (= kokonaishinta "Kokonaishinta") "Kokonaishinta otsikko oikein"))

(defn parsi-paallystyskohteet [workbook]
  (let [sivu (first (xls/sheet-seq workbook)) ;; Käsitellään excelin ensimmäinen sivu tai tabi
        raaka-data (->> sivu
                        xls/row-seq
                        (remove nil?)
                        (map xls/cell-seq)
                        (mapv
                          (fn [rivi]
                            (map-indexed (fn [indeksi arvo]
                                           (xls/read-cell arvo))
                                         rivi))))
        ;; Poistetaan otsikkoriviä ylemmät rivit parsinnan kannalta turhana
        otsikot-ja-rivit (drop-while #(not= (first %) "Kohde-ID") raaka-data)
        otsikot (first otsikot-ja-rivit)
        _ (validoi-excelin-otsikot otsikot)
        rivit (remove #(let [sarake-a-sisalto (first %)]
                         ;; Poistetaan rivi kokonaan, mikäli yhaid on nil. Poistaa myös yhteensä-rivin
                         (nil? sarake-a-sisalto))
                      (rest otsikot-ja-rivit))
        kohteet (into []
                      (keep
                        (fn [rivi]
                          {:yhaid (int (nth rivi 0))
                           :sopimuksen-mukaiset-tyot (nth rivi 4)
                           :maaramuutokset (nth rivi 5)
                           :bitumi-indeksi (nth rivi 6)
                           :kaasuindeksi (nth rivi 7)})
                        rivit))]
    kohteet))

(defn tallenna-paallystyskohteet-excelista
  [db user workbook urakka-id]
  (let [kohteet (parsi-paallystyskohteet workbook)]
    (doseq [p kohteet]
      (yllapitokohteet-q/tallenna-yllapitokohteen-kustannukset-yhaid! db {:yhaid (:yhaid p)
                                                                          :urakka urakka-id
                                                                          :sopimuksen_mukaiset_tyot (:sopimuksen-mukaiset-tyot p)
                                                                          :bitumi_indeksi (:bitumi-indeksi p)
                                                                          :kaasuindeksi (:kaasuindeksi p)
                                                                          :maaramuutokset (:maaramuutokset p)
                                                                          :muokkaaja (:id user)}))))

(defn- excelin-rivi
  [{:keys [yhaid kohdenumero tunnus nimi
           sopimuksen-mukaiset-tyot maaramuutokset arvonvahennykset
           sakot-ja-bonukset bitumi-indeksi kaasuindeksi kokonaishinta]} vuosi]
  (let [yhteiset-arvot-alku [yhaid
                             kohdenumero tunnus nimi
                             sopimuksen-mukaiset-tyot ;; = Tarjoushinta
                             maaramuutokset]
        loppusarake (if (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
                      "H"
                      "J")
        yhteiset-arvot-loppu [bitumi-indeksi ;; = Sideaineen hintamuutokset
                              kaasuindeksi ;; = Nestekaasun ja kevyen polttoöljyn hintamuutokset
                              ;; kokonaishinta lasketaan muista sarakkeista, älä siis salli muokkausta
                              [:kaava {:kaava :summaa-vieressaolevat
                                       :alkusarake "E"
                                       :loppusarake loppusarake}]]]
    (into []
          (concat yhteiset-arvot-alku
                  (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
                    [arvonvahennykset sakot-ja-bonukset])
                  yhteiset-arvot-loppu))))

(defn- excelin-sarakkeet [vuosi]
  (let [yhteiset-sarakkeet-alku [{:otsikko "Kohde-ID" :tasaa :oikea :fmt :kokonaisluku}
                                 {:otsikko "Kohdenro" :tasaa :oikea}
                                 {:otsikko "Tunnus"}
                                 {:otsikko "Nimi"}
                                 {:otsikko "Tarjoushinta"  :fmt :raha :voi-muokata? true}
                                 {:otsikko "Määrämuutokset" :fmt :raha}]
        yhteiset-sarakkeet-loppu [{:otsikko "Sideaineen hintamuutokset" :fmt :raha :voi-muokata? true}
                                  {:otsikko "Nestekaasun ja kevyen polttoöljyn hintamuutokset" :fmt :raha :voi-muokata? true}
                                  {:otsikko "Kokonaishinta" :fmt :raha}]]
    (into []
          (concat yhteiset-sarakkeet-alku
                  (when-not (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
                    [{:otsikko "Arvonmuutokst" :tasaa :oikea :fmt :raha}
                     {:otsikko "Sakko/Bonus" :tasaa :oikea :fmt :raha}])
                  yhteiset-sarakkeet-loppu))))

(defn muodosta-excelrivit [kohteet vuosi]
  (let [kohteet (map #(assoc % :kokonaishinta
                               (yllapitokohteet-domain/yllapitokohteen-kokonaishinta % vuosi))
                     kohteet)
        kohderivit (mapcat
                     (fn [kohde]
                       [{:rivi (excelin-rivi kohde vuosi)
                         :lihavoi? false}])
                     kohteet)
        tyhjat-rivit (for [i (range 0 2)]
                       (into []
                             (repeat (if (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
                                       9
                                       11)
                                     nil)))
        eka-rivi-jossa-kustannuksia 5
        yhteenvetorivi [(into []
                              (concat
                                [nil nil nil "Yhteensä:"]
                                (into []
                                      (repeat (if (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
                                                5
                                                7)
                                              [:kaava {:kaava :summaa-yllaolevat
                                                       :alkurivi eka-rivi-jossa-kustannuksia
                                                       :loppurivi (+ (count kohderivit)
                                                                     (- eka-rivi-jossa-kustannuksia 1))}]))))]]
    (concat
      (when (> (count kohteet) 0)
        kohderivit)
      tyhjat-rivit
      yhteenvetorivi)))

(defn vie-paallystyskohteet-exceliin
  [db workbook user {:keys [urakka-id vuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (let [urakka (first (q-urakat/hae-urakka db urakka-id))
        alkupvm (pvm/vuoden-eka-pvm vuosi)
        loppupvm (pvm/vuoden-viim-pvm vuosi)
        kohteet (yllapitokohteet-domain/jarjesta-yllapitokohteet
                  (yllapitokohteet/hae-urakan-yllapitokohteet db user tiedot))
        sarakkeet (excelin-sarakkeet vuosi)
        rivit (muodosta-excelrivit kohteet vuosi)
        optiot {:nimi "Päällystysskohteet"
                :sheet-nimi "HARJAAN"
                :varjele-sheet-muokkauksilta? true
                :tyhja (if (empty? kohteet) "Ei päällystyskohteita.")
                :rivi-ennen [{:teksti "" :sarakkeita 4}
                             {:teksti "Kustannukset (€)"
                              :sarakkeita (if (yllapitokohteet-domain/piilota-arvonmuutos-ja-sanktio? vuosi)
                                            5
                                            7)}]}
        taulukot [[:taulukko optiot sarakkeet
                   rivit]]
        tiedostonimi (str (:nimi urakka) "-Päällystyskohteet-" vuosi)
        taulukko (concat
                   [:raportti {:nimi tiedostonimi
                               :raportin-yleiset-tiedot {:custom-ylin-rivi "TIEDONSIIRTOLOMAKE HARJAAN"
                                                         :raportin-nimi "Päällystyskohteet"
                                                         :urakka (:nimi urakka)
                                                         :alkupvm (pvm/kokovuosi-ja-kuukausi alkupvm)
                                                         :loppupvm (pvm/kokovuosi-ja-kuukausi loppupvm)}
                               :orientaatio :landscape
                               :viimeinen-rivi-yhteenveto? true}]
                   (if (empty? taulukot)
                     [[:taulukko optiot nil [["Ei päällystyskohteita"]]]]
                     taulukot))]
    (excel/muodosta-excel (vec taulukko)
                          workbook)))
