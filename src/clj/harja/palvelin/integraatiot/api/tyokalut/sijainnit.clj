(ns harja.palvelin.integraatiot.api.tyokalut.sijainnit
  (:require [harja.kyselyt.tieverkko :as tieverkko]
            [taoensso.timbre :as log]
            [harja.geo :as geo])
  (:import (org.postgresql.util PSQLException)
           (org.postgis Point LineString)))

(defn hae-tierekisteriosoite [db {alku-x :x alku-y :y} {loppu-x :x loppu-y :y}]
  (let [threshold 250]
    (try
      (if (and alku-x alku-y loppu-x loppu-y)
        (first (tieverkko/hae-tr-osoite-valille db alku-x alku-y loppu-x loppu-y threshold))
        (when (and alku-x alku-y)
          (first (tieverkko/hae-tr-osoite db alku-x alku-y threshold))))
      (catch PSQLException e
        (log/error e "Sijainnin hakemisessa tapahtui poikkeus.")
        nil))))

(defn tee-geometria [{alku-x :x alku-y :y} {loppu-x :x loppu-y :y}]
  (if (and alku-x alku-y loppu-x loppu-y)
    (geo/geometry (LineString. (into-array [(Point. alku-x alku-y) (Point. loppu-x loppu-y)])))
    (when (and alku-x alku-y)
      (geo/geometry (Point. alku-x alku-y)))))
