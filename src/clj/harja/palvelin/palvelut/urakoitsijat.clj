(ns harja.palvelin.palvelut.urakoitsijat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.skeema :as skeema]
            [harja.kyselyt.urakoitsijat :as q]))

(declare hae-urakoitsijat)
                        
  
(defrecord Urakoitsijat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakoitsijat (fn [user _]
                                         (hae-urakoitsijat (:db this) user)))
    
    this)
  
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-urakoitsijat)
    this))


(defn hae-urakoitsijat
  "Palvelu, joka palauttaa kaikki urakoitsijat urakkatyypistä riippumatta."
  [db user]  
  (-> (q/listaa-urakoitsijat db)
      vec))



  
  
  
