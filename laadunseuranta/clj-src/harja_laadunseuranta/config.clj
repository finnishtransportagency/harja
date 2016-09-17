(ns harja-laadunseuranta.config
  (:require [clojure.edn :as edn]))

(def +asetustiedosto+ "laadunseuranta/laadunseuranta_asetukset.edn")

(def config (atom nil))

(defn aseta-config! [c]
  (reset! config c))
