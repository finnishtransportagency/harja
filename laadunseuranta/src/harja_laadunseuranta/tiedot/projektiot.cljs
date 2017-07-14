(ns harja-laadunseuranta.tiedot.projektiot
  (:require [harja.geo :as geo]
            [ol.proj :as ol-proj]
            [ol.extent :as ol-extent]))

(def +suomen-extent+ [-548576.000000, 6291456.000000, 1548576.000000, 8388608.000000])

(def projektio (ol-proj/Projection. #js {:code geo/+etrs-tm35fin+
                                         :extent (clj->js +suomen-extent+)}))

(defn latlon-vektoriksi [{:keys [lat lon]}]
  [lon lat])

(defn tilegrid [jakoja]
  (let [koko (/ (ol-extent/getWidth (.getExtent projektio)) (* jakoja jakoja))
        jaot (range jakoja)]
    {:origin (js->clj (ol-extent/getTopLeft (.getExtent projektio)))
     :resolutions (vec (map-indexed (fn [i v] (/ koko (Math/pow 2 i))) jaot))
     :matrixIds (vec jaot)}))
