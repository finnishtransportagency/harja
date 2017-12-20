(ns harja.palvelin.raportointi.raportit.kanavien-laskutusyhteenveto
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.kyselyt.toimenpideinstanssit :as tpi-q]
            [harja.kyselyt.urakat :as urakat-q]
            ))

(defqueries "harja/palvelin/raportointi/raportit/kanavien_laskutusyhteenveto.sql")

(defn- kokonais-tai-yksikkohintainen? [tyyppi]
  (some #(= tyyppi %) [:kokonaishintaiset :yksikkohintaiset]))

(defn- sarakkeet [tyyppi]
  [{:leveys 3 :otsikko "Toimen\u00ADpide"}
   {:leveys 1 :otsikko (when (kokonais-tai-yksikkohintainen? tyyppi)
                         "Suunni\u00ADtellut") :fmt :raha}
   {:leveys 1 :otsikko "Toteutunut" :fmt :raha}
   {:leveys 1 :otsikko (when (kokonais-tai-yksikkohintainen? tyyppi)
                         "Jäljellä") :fmt :raha}])

(defn- kaikki-yhteensa-rivit [kokonaishintaiset muutos-ja-lisatyot sanktiot erilliskustannukset]
  (let [kaikkien-kululajien-rivit (concat kokonaishintaiset muutos-ja-lisatyot sanktiot erilliskustannukset)
        toimenpideinstansseittain (group-by :tpi-nimi kaikkien-kululajien-rivit)
        rivit (conj
                (into []
                      (concat
                        (for [tpin-rivit toimenpideinstansseittain]
                          [(key tpin-rivit)
                           ""
                           (reduce + 0 (keep :toteutunut-maara (val tpin-rivit)))
                           ""]
                          )))

                ["Yhteensä"
                 ""
                 (reduce + 0 (keep :toteutunut-maara kaikkien-kululajien-rivit))
                 ""])]
    rivit))


(defn- toimenpiteiden-summa [kentat]
  (reduce + (keep identity kentat)))

(defn- kentan-summa [tietorivit kentta]
  (toimenpiteiden-summa (map kentta tietorivit)))

(defn- tpi-kohtaiset-rivit [tietorivit tyyppi]
  (into []
        (concat
          (for [rivi tietorivit]
            [(:tpi-nimi rivi)
             (if (kokonais-tai-yksikkohintainen? tyyppi)
               (or (:suunniteltu-maara rivi) 0)
               "")
             (or (:toteutunut-maara rivi) 0)
             (if (kokonais-tai-yksikkohintainen? tyyppi)
               (- (or (:suunniteltu-maara rivi) 0)
                 (or (:toteutunut-maara rivi) 0))
               "")]))))

(defn- summarivi [tietorivit tyyppi]
  (let [kaikki-suunnitellut (kentan-summa tietorivit :suunniteltu-maara)
        kaikki-toteutuneet (kentan-summa tietorivit :toteutunut-maara)]
    ["Yhteensä" (if (kokonais-tai-yksikkohintainen? tyyppi)
                  kaikki-suunnitellut
                  "")
     kaikki-toteutuneet
     (if (kokonais-tai-yksikkohintainen? tyyppi)
       (- kaikki-suunnitellut
         kaikki-toteutuneet)
       "")]))

(defn- kulutyypin-rivit [tietorivit tyyppi]
  (conj
    (tpi-kohtaiset-rivit tietorivit tyyppi)
    (summarivi tietorivit tyyppi)))

(defn- taulukko [otsikko tyyppi data]
  [:taulukko {:otsikko otsikko
              :tyhja (when (empty? data) "Ei raportoitavaa.")
              :sheet-nimi otsikko
              :viimeinen-rivi-yhteenveto? true}
   (sarakkeet tyyppi)
   (kulutyypin-rivit data tyyppi)])

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (let [urakan-nimi (:nimi (first (urakat-q/hae-urakka db urakka-id)))
        raportin-nimi "Laskutusyhteenveto"
        raportin-otsikko (raportin-otsikko urakan-nimi raportin-nimi alkupvm loppupvm)
        hakuparametrit {:urakkaid urakka-id :alkupvm alkupvm :loppupvm loppupvm}
        kokonaishintaiset (hae-kokonaishintaiset-toimenpiteet db hakuparametrit)
        muutos-ja-lisatyot (map #(assoc % :toteutunut-maara
                                   (+ (:summat %)
                                      (:summat_kan_hinta_yksikkohinnalla %)
                                      (:summat_yht_yksikkohinnalla %)))
                                (hae-muutos-ja-lisatyot db hakuparametrit))
        sanktiot (hae-sanktiot db hakuparametrit)
        erilliskustannukset (hae-erilliskustannukset db hakuparametrit)]
    (log/debug "Kanavien Laskutusyhteenveto, suorita: " parametrit)

    [:raportti {:orientaatio :landscape
                :nimi raportin-otsikko}

     (taulukko "Kokonaishintaiset työt" :kokonaishintaiset kokonaishintaiset)
     (taulukko "Muutos- ja lisätyöt" :muutos-ja-lisatyot muutos-ja-lisatyot)
     (taulukko "Sanktiot" :sanktiot sanktiot)
     (taulukko "Erilliskustannukset" :erilliskustannukset erilliskustannukset)

     (let [kaikki-yht-rivit (kaikki-yhteensa-rivit kokonaishintaiset muutos-ja-lisatyot
                                                   sanktiot erilliskustannukset)]
       [:taulukko {:otsikko "Kaikki yhteensä"
                   :tyhja (when (empty? kaikki-yht-rivit) "Ei raportoitavaa.")
                   :sheet-nimi "Yhteensä"
                   :viimeinen-rivi-yhteenveto? true}
        (sarakkeet :yhteenveto)
        kaikki-yht-rivit])]))
