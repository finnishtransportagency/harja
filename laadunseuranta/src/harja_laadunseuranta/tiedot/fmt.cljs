(ns harja-laadunseuranta.tiedot.fmt
  (:require [ol.proj :as ol-proj]
            [ol.extent :as ol-extent]
            [clojure.string :as str]))

(defn string->numero [arvo]
  (js/parseFloat (str/replace arvo "," ".")))

(defn n-desimaalia [arvo n]
  (string->numero (.toFixed arvo 2)))