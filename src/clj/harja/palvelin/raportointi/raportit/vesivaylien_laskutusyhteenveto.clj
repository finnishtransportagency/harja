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
  [{:otsikko "Kokonaishintaiset: kauppamerenkulku"}
   ["TODO"]
   {:otsikko "Kokonaishintaiset: muut"}
   ["TODO"]
   {:otsikko "Yksikköhintaiset: kauppamerenkulku"}
   ["TODO"]
   {:otsikko "Yksikköhintaiset: muut"}
   ["TODO"]])

(defn- raportin-sarakkeet []
  [{:leveys 3 :otsikko "Toimenpide / Maksuerä"}
   {:leveys 1 :otsikko "Maksuerät"}
   {:leveys 1 :otsikko "Tunnus"}
   {:leveys 1 :otsikko "Tilausvaltuus [t €]"}
   {:leveys 1 :otsikko "Suunnitellut [t €]"}
   {:leveys 1 :otsikko "Toteutunut [t €]"}
   {:leveys 1 :otsikko "Yhteensä (S+T) [t €]"}
   {:leveys 1 :otsikko "Jäljellä [€]"}
   {:leveys 1 :otsikko "Yhteensä jäljellä (hoito ja käyttö)"}])

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
        raportin-nimi "Laskutusyhteenveto"]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko "Projekti"
                 :tyhja (if (empty? raportin-rivit) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi}
      (raportin-sarakkeet)
      raportin-rivit]]))
