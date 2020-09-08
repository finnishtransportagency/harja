(ns harja.palvelin.raportointi.raportit.kulut-tehtavaryhmittain
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.tarkastukset :as tarkastukset-q]
            [harja.kyselyt
             [laskut :as kulut-q]
             [aliurakoitsijat :as ali-q]
             [kustannusarvioidut-tyot :as kust-q]]))

(defn- kulut-urakalle
  [db user opts])

(defn- kulut-elylle
  [db user opts])

(defn- kulut-koko-maalle
  [db user {:keys [alkupvm loppupvm] :as opts}]
  (kulut-q/hae-koko-maan-kulut-raporttiin-aikavalilla db {:alkupvm alkupvm
                                                          :loppupvm loppupvm}))

(defn- kulut-tehtavaryhmittain
  [db user {:keys [urakka-id ely-id] :as opts}]
  (mapv (fn [asia]
          [(:tehtavaryhma asia) (:summa asia)])
    (cond
    urakka-id (kulut-urakalle db user opts)
    ely-id (kulut-elylle db user opts)
    :default (kulut-koko-maalle db user opts))))

(defn suorita
  [db user {:keys [urakka-id] :as parametrit}]
  (let [rivit (kulut-tehtavaryhmittain db user parametrit)]
    [:raportti {:nimi "Kulut tehtäväryhmittäin"}
     [:teksti (str "Rapsapapsa!" (pr-str parametrit)
                   (pr-str rivit))]
     [:taulukko
      {}
      [{:leveys 1 :otsikko "Tehtäväryhmä"} {:leveys 1 :otsikko "Summa"}]
      rivit]]))