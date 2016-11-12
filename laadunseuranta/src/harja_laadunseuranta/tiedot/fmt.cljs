(ns harja-laadunseuranta.tiedot.fmt
  (:require [ol.proj :as ol-proj]
            [ol.extent :as ol-extent]
            [cljs-time.format :as time-fmt]
            [clojure.string :as str]))

(defn string->numero [arvo]
  (js/parseFloat (str/replace arvo "," ".")))

(defn n-desimaalia [arvo n]
  (string->numero (.toFixed arvo 2)))

(def pvm-fmt (time-fmt/formatter "dd.MM.yyyy"))
(def klo-fmt (time-fmt/formatter "HH:mm"))