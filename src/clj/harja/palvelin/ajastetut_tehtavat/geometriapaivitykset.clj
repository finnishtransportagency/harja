(ns harja.palvelin.ajastetut-tehtavat.geometriapaivitykset
  (:require [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [clj-time.periodic :refer [periodic-seq]]
            [taoensso.timbre :as log]
            [chime :refer [chime-at]]))

(defn hae-aikataulu [db paivitys]
  )

(defn tee-tieverkon-paivitystehtava [this]
  (log/debug "Ajastetaan suolasakkojen merkitseminen likaiseksi.")
  (chime-at (hae-aikataulu db "tieverkko")
            (fn [_]
              (log/debug "Merkitään suolasakot likaisiksi seuraavaa Sampo-lähetystä varten")
              (merkitse-sakkomaksuerat-likaisiksi (:db this)))))

(defrecord Geometriapaivitykset []
  component/Lifecycle
  (start [this]
    (assoc this :tieverkon-paivitystehtava (tee-tieverkon-paivitystehtava this)))
  (stop [this]
    (:tieverkon-paivitystehtava this)
    this))
