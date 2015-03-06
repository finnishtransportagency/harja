(ns harja.palvelin.palvelut.yksikkohintaiset-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [clojure.tools.logging :as log]
            [clojure.set :refer [intersection difference]]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            
            [harja.kyselyt.yksikkohintaiset-tyot :as q]))

(declare hae-urakan-yksikkohintaiset-tyot)
                        
(defrecord Yksikkohintaiset-tyot []
  component/Lifecycle
  (start [this]
   (doto (:http-palvelin this)
     (julkaise-palvelu
       :yksikkohintaiset-tyot (fn [user urakka-id]
         (hae-urakan-yksikkohintaiset-tyot (:db this) user urakka-id))))
   this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :yksikkohintaiset-tyot)
    this))


(defn hae-urakan-yksikkohintaiset-tyot 
  "Palvelu, joka palauttaa urakan yksikkohintaiset työt."
  [db user urakka-id]
  (log/info "hae-urakan-yksikkohintaiset-tyot" urakka-id)
  ;; FIXME: oikeus checki tähän tai queryyn urakkaroolin kautta
  (into []
        (q/listaa-urakan-yksikkohintaiset-tyot db urakka-id)))