(ns harja.ui.openlayers.projektiot
  "Karttaa varten tarvittavat extent alueet ja projektiot"
  (:require [ol.proj :as ol-proj]))

(def suomen-extent
  "Suomalaisissa kartoissa olevan projektion raja-arvot."
  [-548576.000000 6291456.000000 1548576.000000 8388608.000000])

(def projektio (ol-proj/Projection. #js {:code   "EPSG:3067"
                                         :extent (clj->js suomen-extent)}))
