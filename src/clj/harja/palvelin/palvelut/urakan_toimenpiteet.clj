(ns harja.palvelin.palvelut.urakan-toimenpiteet
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            
            [harja.kyselyt.urakan-toimenpiteet :as q]))

(declare hae-urakan-toimenpiteet-ja-tehtavat hae-urakan-toimenpiteet)
                        
(defrecord Urakan-toimenpiteet []
  component/Lifecycle
  (start [this]
   (doto (:http-palvelin this)
     (julkaise-palvelu
       :urakan-toimenpiteet-ja-tehtavat (fn [user urakka-id]
                                          (hae-urakan-toimenpiteet-ja-tehtavat (:db this) user urakka-id)))
     (julkaise-palvelu
       :urakan-toimenpiteet (fn [user urakka-id]
                              (hae-urakan-toimenpiteet (:db this) user urakka-id))))
   this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :urakan-toimenpiteet-ja-tehtavat)
    (poista-palvelu (:http-palvelin this) :urakan-toimenpiteet)
    this))


(defn hae-urakan-toimenpiteet
  "Palvelu, joka palauttaa urakan toimenpiteet"
  [db user urakka-id]
        (into []
              (q/hae-urakan-toimenpiteet db urakka-id)))


(defn hae-urakan-toimenpiteet-ja-tehtavat
  "Palvelu, joka palauttaa urakan toimenpiteet ja tehtävät"
  [db user urakka-id]
  (into []
        (q/hae-urakan-toimenpiteet-ja-tehtavat-tasot db urakka-id)))
