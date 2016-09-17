(ns harja-laadunseuranta.config
  (:require [clojure.edn :as edn]))

(def +asetustiedosto+ "laadunseuranta/laadunseuranta_asetukset.edn")

(def config (delay (edn/read-string (slurp +asetustiedosto+))))
