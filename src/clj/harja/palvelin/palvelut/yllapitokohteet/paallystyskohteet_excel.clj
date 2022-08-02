(ns harja.palvelin.palvelut.yllapitokohteet.paallystyskohteet-excel
  "Vie päällystyskohteiden kustannukset exceliin ja tuo samat tiedot Excelistä sisään."
  (:require [dk.ative.docjure.spreadsheet :as xls]
            [clojure.set :as set]
            [clojure.string :refer [trim]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.palvelin.palvelut.yllapitokohteet :as yllapitokohteet]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.pvm :as pvm]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain])
  (:import (org.apache.poi.ss.util CellRangeAddress)
           (java.util Date)))


(defn- excelin-rivi
  [{:keys [yhaid kohdenumero tunnus nimi
           sopimuksen-mukaiset-tyot maaramuutokset arvonvahennykset
           sakot-ja-bonukset bitumi-indeksi kaasuindeksi kokonaishinta]} vuosi]
  (let [yhteiset-arvot-alku [yhaid
                             kohdenumero tunnus nimi
                             sopimuksen-mukaiset-tyot ;; = Tarjoushinta
                             maaramuutokset]
        yhteiset-arvot-loppu [bitumi-indeksi ;; = Sideaineen hintamuutokset
                              kaasuindeksi ;; = Nestekaasun ja kevyen polttoöljyn hintamuutokset
                              kokonaishinta]]
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
                                 {:otsikko "Tarjoushinta" :tasaa :oikea :fmt :raha}
                                 {:otsikko "Määrämuutokset" :tasaa :oikea :fmt :raha}]
        yhteiset-sarakkeet-loppu [{:otsikko "Sideaineen hintamuutokset" :tasaa :oikea :fmt :raha}
                                  {:otsikko "Nestekaasun ja kevyen polttoöljyn hintamuutokset" :tasaa :oikea :fmt :raha}
                                  {:otsikko "Kokonaishinta" :tasaa :oikea :fmt :raha}]]
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
                                              [:kaava {:kaava :summaa-yllaolevat :alkurivi eka-rivi-jossa-kustannuksia}]))))]]
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
                :tyhja (if (empty? kohteet) "Ei päällystyskohteita.")
                :lista-tyyli? false
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
