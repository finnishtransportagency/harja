(ns harja.palvelin.palvelut.hallintayksikot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.skeema :as skeema]
            [harja.kyselyt.hallintayksikot :as q]
            [harja.geo :refer [muunna-pg-tulokset]]))

(declare hae-hallintayksikot)
                        
  
(defrecord Hallintayksikot []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hallintayksikot (fn [user liikennemuoto]
                                         (hae-hallintayksikot (:db this) user liikennemuoto)))
    
    this)
  
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hallintayksikot)))


(defn hae-hallintayksikot
  "Palvelu, joka palauttaa halutun liikennemuodon hallintayksiköt."
  [db user liikennemuoto]  
  (-> (q/listaa-hallintayksikot-kulkumuodolle db (case liikennemuoto
                                                   :tie "T"
                                                   :vesi "V"
                                                   :rata "R"))
      (muunna-pg-tulokset :alue)
      vec))



  
  
  
