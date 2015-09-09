(ns harja.palvelin.palvelut.tierek-haku
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.tieverkko :as tv]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]))

(defn hae-tr-pisteella [db user params]
  (when-let [tros (first (tv/hae-tr-osoite-valille db
                                                   (:x1 params) (:y1 params)
                                                   (:x2 params) (:y2 params)
                                                   250))]
    (konv/array->vec tros :tr_osoite)))

(defrecord TierekisteriHaku []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-tr-pisteella (fn [user params]
                                       (hae-tr-pisteella (:db this) user params)))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-tr-pisteella)
    this))
