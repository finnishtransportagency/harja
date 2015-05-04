(ns harja.palvelin.palvelut.toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.toteumat :as q]
            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]))

(def toteuma-xf
  (comp (map #(konv/array->vec % :tehtavat))))

(defn urakan-toteumat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan toteumat: " urakka-id)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        toteuma-xf
        (q/listaa-urakan-toteumat db urakka-id sopimus-id (konv/sql-date alkupvm) (konv/sql-date loppupvm))))

(defn urakan-toteuma-paivat [db user {:keys [urakka-id sopimus-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan toteumapäivän: " urakka-id)
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into #{}
        (map :paiva)
        (q/hae-urakan-toteuma-paivat db urakka-id sopimus-id (konv/sql-date alkupvm) (konv/sql-date loppupvm))))

                             
                          
(defn tallenna-toteuma [db user toteuma]
  (validoi Toteuma toteuma)
  
                                        
(defrecord Toteumat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)]
      (julkaise-palvelu http :urakan-toteumat
                        (fn [user tiedot]
                          (urakan-toteumat (:db this) user tiedot)))
      (julkaise-palvelu http :urakan-toteuma-paivat
                        (fn [user tiedot]
                          (urakan-toteuma-paivat (:db this) user tiedot)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this) :urakan-toteumat :urakan-toteuma-paivat)
    this))
