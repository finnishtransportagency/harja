(ns harja-laadunseuranta.tiedot.fmt
  (:require [ol.proj :as ol-proj]
            [ol.extent :as ol-extent]
            [cljs-time.format :as time-fmt]
            [clojure.string :as str]))

(defn string->numero [arvo]
  (js/parseFloat (str/replace arvo "," ".")))

(defn n-desimaalia [arvo n]
  (string->numero (.toFixed arvo n)))

(def pvm-formatter (time-fmt/formatter "dd.MM.yyyy"))
(def klo-formatter (time-fmt/formatter "HH:mm"))

(defn pvm [aikaleima]
  (time-fmt/unparse pvm-formatter aikaleima))

(defn klo [aikaleima]
  (time-fmt/unparse klo-formatter aikaleima))

(defn pvm-klo [aikaleima]
  (str (pvm aikaleima) " " (klo aikaleima)))