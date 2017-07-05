(ns harja.domain.tiemerkinta
  "Tiemerkinnän asiat"
  (:require
    #?(:cljs [cljs-time.core :as t])
    #?(:cljs [cljs-time.extend])
    #?(:clj
            [clj-time.core :as t])))

(def tiemerkinnan-suoritusaika-paivina (t/days 14))

(defn tiemerkinta-oltava-valmis [tiemerkintapvm]
  (when (some? tiemerkintapvm)
    (t/plus tiemerkintapvm tiemerkinnan-suoritusaika-paivina)))
