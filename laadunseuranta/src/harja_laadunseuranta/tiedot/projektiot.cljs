(ns harja-laadunseuranta.tiedot.projektiot
  (:require [ol.proj :as ol-proj]
            [ol.extent :as ol-extent]))

(def +etrs-tm35fin+ "EPSG:3067")
(def +wgs84+ "EPSG:4326")
(def +suomen-extent+ [-548576.000000, 6291456.000000, 1548576.000000, 8388608.000000])

(def projektio (ol-proj/Projection. #js {:code   +etrs-tm35fin+
                                         :extent (clj->js +suomen-extent+)}))

(defn latlon-vektoriksi [{:keys [lat lon]}]
  [lon lat])

(defn wgs84->etrsfin [[lon lat]]
  (ol-proj/transform (clj->js [lon lat]) +wgs84+ +etrs-tm35fin+))

(defn tilegrid [jakoja]
  (let [koko (/ (ol-extent/getWidth (.getExtent projektio)) (* jakoja jakoja))
        jaot (range jakoja)]
    {:origin (js->clj (ol-extent/getTopLeft (.getExtent projektio)))
     :resolutions (vec (map-indexed (fn [i v] (/ koko (Math/pow 2 i))) jaot))
     :matrixIds (vec jaot)}))
