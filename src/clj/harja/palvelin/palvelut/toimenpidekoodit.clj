(ns harja.palvelin.palvelut.toimenpidekoodit
   (:require [com.stuartsierra.component :as component]
             [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
             [harja.skeema :as skeema]

             [harja.kyselyt.toimenpidekoodit :refer [hae-kaikki-toimenpidekoodit]]))

(declare hae-toimenpidekoodit)

(defrecord Toimenpidekoodit []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this) :hae-toimenpidekoodit
                      (fn [kayttaja _]
                        (hae-toimenpidekoodit (:db this) kayttaja)))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-toimenpidekoodit)
    this))


(defn hae-toimenpidekoodit
  "Palauttaa toimenpidekoodit"
  [db kayttaja]
  (loop [acc {}
         [tpk & koodit] (hae-kaikki-toimenpidekoodit db)]
    (if-not tpk
      acc
      (recur (assoc acc
               (:koodi tpk)
               (dissoc tpk :koodi))
             koodit))))
       

