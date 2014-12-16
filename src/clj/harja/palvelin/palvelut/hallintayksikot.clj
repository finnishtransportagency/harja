(ns harja.palvelin.palvelut.hallintayksikot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.skeema :as skeema]
            [harja.kyselyt.hallintayksikot :as q]))

(declare hae-hallintayksikot)
                        
  
(defrecord Hallintayksikot []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hallintayksikot (fn [user ehdot]
                                         (hae-hallintayksikot (:db this) user ehdot)))
    
    this)
  
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hallintayksikot)))


(defn hae-hallintayksikot
  "Palvelu, joka palauttaa hakuehtoihin soveltuvat hallintayksiköt, joihin annetulle käyttäjälle on näkymäoikeus."
  [db user ehdot]
  (->> (q/listaa-hallintayksikot-kulkumuodolle db (case (:liikennemuoto ehdot)
                                                    :tie "T"
                                                    :vesi "V"
                                                    :rata "R"))
       ;; Normalisoi PGPolygon clojure dataksi (FIXME: tästä joku geo utility namespace
       (mapv #(if-let [alue (:alue %)]
                (assoc % :alue (mapv (fn [p]
                                       [(.x p) (.y p)])
                                     (seq (.points (:alue %)))))
                %))))


  
  
  
