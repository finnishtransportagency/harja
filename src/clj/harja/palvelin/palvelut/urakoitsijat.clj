(ns harja.palvelin.palvelut.urakoitsijat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.tools.logging :as log]
            [harja.kyselyt.urakoitsijat :as q]))

(declare hae-urakoitsijat urakkatyypin-urakoitsijat yllapidon-urakoitsijat)
                        
  
(defrecord Urakoitsijat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-urakoitsijat (fn [user _]
       (hae-urakoitsijat (:db this) user)))
    (julkaise-palvelu (:http-palvelin this) :urakkatyypin-urakoitsijat
      (fn [user urakkatyyppi]
        (urakkatyypin-urakoitsijat (:db this) user urakkatyyppi)))
    (julkaise-palvelu (:http-palvelin this) :yllapidon-urakoitsijat 
      (fn [user]
        (yllapidon-urakoitsijat (:db this) user)))
    this)
  
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-urakoitsijat)
    (poista-palvelu (:http-palvelin this) :urakkatyypin-urakoitsijat )
    (poista-palvelu (:http-palvelin this) :yllapidon-urakoitsijat)
    this))


(defn hae-urakoitsijat
  "Palvelu, joka palauttaa kaikki urakoitsijat urakkatyypistä riippumatta."
  [db user]  
  (-> (q/listaa-urakoitsijat db)
      vec))


(defn urakkatyypin-urakoitsijat [db user urakkatyyppi]
  (log/debug "Haetaan urakkatyypin " urakkatyyppi " urakoitsijat")
  (->> (q/hae-urakkatyypin-urakoitsijat db (name urakkatyyppi))
      (map :id)
      (into #{})))

(defn yllapidon-urakoitsijat [db user]
  (log/debug "Haetaan ylläpidon urakoitsijat")
  (->> (q/hae-yllapidon-urakoitsijat db)
      (map :id)
      (into #{})))
  
  
