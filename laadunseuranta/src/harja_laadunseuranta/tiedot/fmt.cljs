(ns harja-laadunseuranta.tiedot.fmt
  (:require [ol.proj :as ol-proj]
            [ol.extent :as ol-extent]
            [clojure.string :as str]))

(defn kahdella-desimaalilla [arvo]
  (gstr/format "%.2f" arvo))

(defn string->numero [arvo]
  (js/parseFloat (str/replace arvo "," ".")))