(ns harja-laadunseuranta.tiedot.math
  (:require [ol.proj :as ol-proj]
            [ol.extent :as ol-extent]))

(defn- avg [mittaukset]
  (/ (reduce + 0 mittaukset) (count mittaukset)))