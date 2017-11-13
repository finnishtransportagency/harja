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

(defn- kaikki-yhteensa-rivit [kokonaishintaiset sanktiot erilliskustannukset]
  ["Yhteensä"
   ""
   (reduce + 0 (keep :toteutunut-maara (concat kokonaishintaiset sanktiot erilliskustannukset)))
   ""])


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

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (let [urakan-nimi (:nimi (first (urakat-q/hae-urakka db urakka-id)))
        raportin-nimi "Laskutusyhteenveto"
        raportin-otsikko (raportin-otsikko urakan-nimi raportin-nimi alkupvm loppupvm)
        hakuparametrit {:urakkaid urakka-id :alkupvm alkupvm :loppupvm loppupvm}
        kokonaishintaiset (hae-kokonaishintaiset-toimenpiteet db hakuparametrit)
        ;; TODO Yksikköhintaiset
        sanktiot (hae-sanktiot db hakuparametrit)
        erilliskustannukset (hae-erilliskustannukset db hakuparametrit)]
    (log/debug "Kanavien Laskutusyhteenveto, suorita: " parametrit)

    [:raportti {:orientaatio :landscape
                :nimi raportin-otsikko}

     [:taulukko {:otsikko "Kokonaishintaiset työt"
                 :tyhja (when (empty? kokonaishintaiset) "Ei raportoitavaa.")
                 :sheet-nimi "Kokonaishintaiset"
                 :viimeinen-rivi-yhteenveto? true}
      (sarakkeet :kokonaishintaiset)
      (kulutyypin-rivit kokonaishintaiset :kokonaishintaiset)]

     [:taulukko {:otsikko "Sanktiot"
                 :tyhja (when (empty? sanktiot) "Ei raportoitavaa.")
                 :sheet-nimi "Sanktiot"
                 :viimeinen-rivi-yhteenveto? true}
      (sarakkeet :sanktiot)
      (kulutyypin-rivit sanktiot :sanktiot)]

     [:taulukko {:otsikko "Erilliskustannukset"
                 :tyhja (when (empty? erilliskustannukset) "Ei raportoitavaa.")
                 :sheet-nimi "Erilliskustannukset"
                 :viimeinen-rivi-yhteenveto? true}
      (sarakkeet :erilliskustannuket)
      (kulutyypin-rivit erilliskustannukset :erilliskustannukset)]


     (let [kaikki-yht-rivit (kaikki-yhteensa-rivit kokonaishintaiset sanktiot erilliskustannukset)]
       [:taulukko {:otsikko "Kaikki yhteensä"
                   :tyhja (when (empty? kaikki-yht-rivit) "Ei raportoitavaa.")
                   :sheet-nimi "Yhteensä"
                   :viimeinen-rivi-yhteenveto? true}
        (sarakkeet :yhteenveto)
        [kaikki-yht-rivit]])]))
