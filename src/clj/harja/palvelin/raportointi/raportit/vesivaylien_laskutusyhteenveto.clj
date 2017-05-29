(ns harja.palvelin.raportointi.raportit.vesivaylien-laskutusyhteenveto
  (:require [harja.kyselyt.urakat :as urakat-q]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]))

(defqueries "harja/palvelin/raportointi/raportit/vesivaylien_laskutusyhteenveto.sql")

(defn- muodosta-raportin-rivit [toimenpiteet]
  (mapv (fn [toimenpide]
          [(->> (:reimari-toimenpidetyyppi toimenpide)
               (get to/reimari-toimenpidetyypit)
               to/reimari-toimenpidetyyppi-fmt)])
        toimenpiteet))

(defn- raportin-sarakkeet []
  [{:leveys 2 :otsikko "Toimenpide"}])

(defn hae-raportin-tiedot [{:keys [db urakka-id alkupvm loppupvm]}]
  (hae-vesivaylien-laskutusyhteenveto db {:urakkaid urakka-id
                                          :alkupvm alkupvm
                                          :loppupvm loppupvm}))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        raportin-tiedot (hae-raportin-tiedot {:db db
                                              :urakka-id urakka-id
                                              :alkupvm alkupvm
                                              :loppupvm loppupvm})
        raportin-rivit (muodosta-raportin-rivit raportin-tiedot)
        raportin-nimi "Laskutusyhteenveto"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  "laskutusyhteenveto" alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja (if (empty? raportin-rivit) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi}
      (raportin-sarakkeet)
      raportin-rivit]]))
