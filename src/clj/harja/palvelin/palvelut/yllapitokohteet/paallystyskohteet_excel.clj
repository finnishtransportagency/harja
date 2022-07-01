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


(defn muodosta-excelrivit [kohteet vuosi]
  (let [kohteet (map #(assoc % :kokonaishinta
                                       (yllapitokohteet-domain/yllapitokohteen-kokonaishinta % vuosi))
                             kohteet)
        kohderivit (mapcat
                     (fn [{:keys [yhaid id ;; muille kuin yha-kohteille näytetään Harjan ID, etuliitteen HARJA_<id> kera
                                  kohdenumero tunnus nimi
                                  sopimuksen-mukaiset-tyot ;; = Tarjoushinta
                                  maaramuutokset
                                  bitumi-indeksi ;; = Sideaineen hintamuutokset
                                  kaasuindeksi ;; = Nestekaasun ja kevyen polttoöljyn hintamuutokset
                                  kokonaishinta]}]
                       [{:rivi [(or yhaid
                                    ;; Muille kuin YHA-kohteille näytetään Harja-ID, joka mahdollistaa kustannusten viennin takaisin Harjaan (linkitys)
                                    (str "HARJA_" id))
                                kohdenumero
                                tunnus
                                nimi
                                sopimuksen-mukaiset-tyot
                                maaramuutokset
                                bitumi-indeksi
                                kaasuindeksi
                                kokonaishinta]
                         :lihavoi? false}])
                     kohteet)
        tyhjat-rivit (for [i (range 0 2)]
                       [nil nil nil nil nil nil nil nil nil])
        yhteenvetorivi [[nil nil nil "Yhteensä:"
                         (yllapitokohteet-domain/laske-sarakkeen-summa :sopimuksen-mukaiset-tyot kohteet)
                         (yllapitokohteet-domain/laske-sarakkeen-summa :maaramuutokset kohteet)
                         (yllapitokohteet-domain/laske-sarakkeen-summa :bitumi-indeksi kohteet)
                         (yllapitokohteet-domain/laske-sarakkeen-summa :kaasuindeksi kohteet)
                         (yllapitokohteet-domain/laske-sarakkeen-summa :kokonaishinta kohteet)]]]
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
        kohteet (yllapitokohteet/hae-urakan-yllapitokohteet db user tiedot)
        sarakkeet [{:otsikko "Kohde-ID" :tasaa :oikea :fmt :kokonaisluku}
                   {:otsikko "Kohdenro" :tasaa :oikea}
                   {:otsikko "Tunnus"}
                   {:otsikko "Nimi"}
                   {:otsikko "Tarjoushinta" :tasaa :oikea :fmt :raha}
                   {:otsikko "Määrämuutokset" :tasaa :oikea :fmt :raha}
                   {:otsikko "Sideaineen hintamuutokset" :tasaa :oikea :fmt :raha}
                   {:otsikko "Nestekaasun ja kevyen polttoöljyn hintamuutokset" :tasaa :oikea :fmt :raha}
                   {:otsikko "Kokonaishinta" :tasaa :oikea :fmt :raha}]
        rivit (muodosta-excelrivit kohteet vuosi)
        optiot {:nimi "Päällystysskohteet"
                :sheet-nimi "HARJAAN"
                :tyhja (if (empty? kohteet) "Ei päällystyskohteita.")
                :lista-tyyli? false
                :rivi-ennen [{:teksti "" :sarakkeita 4}
                             {:teksti "Kustannukset (€)" :sarakkeita 5}]}
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
