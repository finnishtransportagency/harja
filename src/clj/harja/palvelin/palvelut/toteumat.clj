(ns harja.palvelin.palvelut.toteumat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.toteumat :as q]
            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]))

(def toteuma-xf
  (comp (map #(-> %
                  (konv/array->vec :tehtavat)
                  (konv/array->vec :materiaalit)))))

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


(defn hae-urakan-tehtavat [db user urakka-id]
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (q/hae-urakan-tehtavat db urakka-id)))
                          
(defn tallenna-toteuma [db user toteuma]
  (validoi Toteuma toteuma)
  (oik/vaadi-kirjoitusoikeus-urakkaan user (:urakka-id toteuma))
  
  (jdbc/with-transaction [c db]
    (let [uusi (q/luo-toteuma<! c (:urakka-id toteuma) (:sopimus-id sopimus)
                                (konv/sql-date (:alkanut toteuma))
                                (konv/sql-date (:paattynyt toteuma))
                                (name (:tyyppi toteuma)))]
      ;; Luodaan uudelle toteumalle tehtävät ja materiaalit
      
      true)))
  
                                        
(defrecord Toteumat []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :urakan-toteumat
                        (fn [user tiedot]
                          (urakan-toteumat db user tiedot)))
      (julkaise-palvelu http :urakan-toteuma-paivat
                        (fn [user tiedot]
                          (urakan-toteuma-paivat db user tiedot)))
      (julkaise-palvelu http :hae-urakan-tehtavat
                        (fn [user urakka-id]
                          (hae-urakan-tehtavat db user urakka-id)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :urakan-toteumat :urakan-toteuma-paivat
                     :hae-urakan-tehtavat)
    this))
