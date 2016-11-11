(ns harja-laadunseuranta.tiedot.math
  (:require [ol.proj :as ol-proj]
            [ol.extent :as ol-extent]))

(defn- avg [arvot]
  (if (empty? arvot)
    0
    (/ (reduce + 0 arvot) (count arvot))))