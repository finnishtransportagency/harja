(ns harja.domain.tiemerkinta
  "Tiemerkinnän asiat"
  (:require
    #?(:cljs [cljs-time.format :as df])
    #?(:cljs [cljs-time.core :as t])
    #?(:cljs [cljs-time.coerce :as tc])
    #?(:cljs [harja.loki :refer [log]])
    #?(:cljs [cljs-time.extend])
    #?(:clj [clj-time.format :as df])
    #?(:clj
            [clj-time.core :as t])
    #?(:clj
            [clj-time.coerce :as tc])
    #?(:clj
            [clj-time.local :as l])
    #?(:clj
            [taoensso.timbre :as log])))

(def tiemerkinnan-suoritusaika-paivina (t/days 14))

(defn tiemerkinta-oltava-valmis [aloituspvm]
  (when (some? aloituspvm)
    (t/plus aloituspvm tiemerkinnan-suoritusaika-paivina)))