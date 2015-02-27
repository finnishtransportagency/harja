(ns harja.palvelin.palvelut.indeksit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.tools.logging :as log]
            [harja.kyselyt.indeksit :as q]))

(declare hae-indeksit)
                        
(defrecord Indeksit []
  component/Lifecycle
  (start [this]
   (julkaise-palvelu (:http-palvelin this)
     :indeksit (fn [user]
       (hae-indeksit (:db this) user)))
   this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :indeksit)
    this))


(defn hae-indeksit
  "Palvelu, joka palauttaa indeksit."
  [db user]
        (let 
          [indeksit-vuosittain  (seq (group-by
                            (fn [rivi]
                              [(:nimi rivi) (:vuosi rivi)]
                              ) (q/listaa-indeksit db)))]
          
          (zipmap (map first indeksit-vuosittain)
                  (map (fn [[_ kuukaudet]]
                         (assoc (zipmap (map :kuukausi kuukaudet) (map #(float (:arvo %)) kuukaudet))
                                :vuosi (:vuosi (first kuukaudet))))
                       indeksit-vuosittain))))


  
  
