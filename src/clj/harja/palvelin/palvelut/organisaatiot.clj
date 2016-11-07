(ns harja.palvelin.palvelut.organisaatiot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.organisaatiot :as q]))

(declare hae-organisaatiot)

(defn hae-organisaatiot
  "Palvelu, joka palauttaa kaikki organisaatiot."
  [db user]
  (-> (q/listaa-organisaatiot db)
      vec))

(defrecord Organisaatiot []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-organisaatiot (fn [user _]
       (hae-organisaatiot (:db this) user)))
    this)
  
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-organisaatiot)
    this))



  
  
