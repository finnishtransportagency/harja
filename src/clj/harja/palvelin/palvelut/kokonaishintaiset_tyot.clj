(ns harja.palvelin.palvelut.kokonaishintaiset-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            
            [harja.palvelin.oikeudet :as oikeudet]
            [harja.kyselyt.kokonaishintaiset-tyot :as q]))

(declare hae-urakan-kokonaishintaiset-tyot)
                        
(defrecord kokonaishintaiset-tyot []
  component/Lifecycle
  (start [this]
   (doto (:http-palvelin this)
     (julkaise-palvelu
       :kokonaishintaiset-tyot (fn [user urakka-id]
         (hae-urakan-kokonaishintaiset-tyot (:db this) user urakka-id))))
   this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :kokonaishintaiset-tyot)
    this))


(defn hae-urakan-kokonaishintaiset-tyot 
  "Palvelu, joka palauttaa urakan kokonaishintaiset työt."
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (map #(assoc % 
                     :summa (if (:summa %) (double (:summa %)))))
        (q/listaa-urakan-kokonaishintaiset-tyot db urakka-id)))