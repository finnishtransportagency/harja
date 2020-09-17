(ns harja.palvelin.raportointi.raportit.kulut-tehtavaryhmittain
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.tarkastukset :as tarkastukset-q]
            [harja.kyselyt
             [laskut :as kulut-q]
             [aliurakoitsijat :as ali-q]
             [kustannusarvioidut-tyot :as kust-q]]
            [harja.pvm :as pvm]))

(defn- kulut-urakalle
  [db user {:keys [alkupvm urakka-id loppupvm]}]
  (let [otsikot [{:leveys 1 :otsikko "Tehtäväryhmä"}
                 {:leveys 1 :otsikko "Hoitokauden alusta"}
                 {:leveys 1 :otsikko "Tässä kuussa"}]
        rivit (mapv #(->
                       [(:tehtavaryhma %) (:summa %)])
                    (kulut-q/hae-urakan-kulut-raporttiin-aikavalilla db {:alkupvm  alkupvm
                                                                         :loppupvm loppupvm
                                                                         :urakka   urakka-id}))]
    {:otsikot otsikot
     :rivit   rivit}))

(defn- kulut-hallintayksikolle
  [db user {:keys [hallintayksikko-id alkupvm loppupvm]}]
  (let [otsikot [{:leveys 1 :otsikko "Tehtäväryhmä"}
                 {:leveys 1 :otsikko "Hoitokauden alusta"}
                 {:leveys 1 :otsikko "Tässä kuussa"}]
        rivit (mapv
                #(->
                   [(:tehtavaryhma %) () (:summa %)])
                (kulut-q/hae-hallintayksikon-kulut-raporttiin-aikavalilla db {:alkupvm         alkupvm
                                                                              :loppupvm        loppupvm
                                                                              :hallintayksikko hallintayksikko-id}))]
    {:otsikot otsikot
     :rivit   rivit}))

(defn- kulut-koko-maalle
  [db user {:keys [alkupvm loppupvm] :as opts}]
  (let [otsikot [{:leveys 1 :otsikko "Tehtäväryhmä"}
                 {:leveys 1 :otsikko "Hoitokauden alusta"}
                 {:leveys 1 :otsikko "Tässä kuussa"}]
        rivit (mapv
                #(->
                   [(:tehtavaryhma %) (:hallintayksikko %) (:summa %)])
                (let [hallintayksikoittain? (get opts "Hallintayksiköittäin eroteltuna?")]
                  (if hallintayksikoittain?
                    (kulut-q/hae-koko-maan-kulut-raporttiin-aikavalilla-hallintayksikoittain db {:alkupvm  alkupvm
                                                                                                 :loppupvm loppupvm})
                    (kulut-q/hae-koko-maan-kulut-raporttiin-aikavalilla db {:alkupvm  alkupvm
                                                                            :loppupvm loppupvm}))))]
    {:otsikot otsikot
     :rivit   rivit}))

(defn- kulut-tehtavaryhmittain
  [db user {:keys [urakka-id hallintayksikko-id] :as opts}]
  (cond
    urakka-id (kulut-urakalle db user opts)
    hallintayksikko-id (kulut-hallintayksikolle db user opts)
    :default (kulut-koko-maalle db user opts)))

(defn suorita
  [db user {:keys [alkupvm loppupvm] :as parametrit}]
  (let [{:keys [otsikot rivit]} (kulut-tehtavaryhmittain db user parametrit)]
    [:raportti {:nimi "Kulut tehtäväryhmittäin"}
     [:otsikko (str "Kulut tehtäväryhmittäin ajalla " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))]
     [:teksti (str "Rapsapapsa!" (pr-str parametrit)
                   (pr-str rivit))]
     [:taulukko
      {}
      otsikot
      rivit]
     [:teksti "Yhteensä:"]
     [:teksti "Urakkavuoden alusta tav.hintaan kuuluvia:"]
     [:teksti "Tavoitehinta:"]
     [:teksti "Jäljellä:"]]))