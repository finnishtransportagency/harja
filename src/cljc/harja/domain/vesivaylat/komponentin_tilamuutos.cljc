(ns harja.domain.vesivaylat.komponentin-tilamuutos
  (:require [clojure.spec.alpha :as s]
            [specql.rel :as rel]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_toimenpiteen_komponenttien_tilamuutokset" ::tpk-tilat])

(def
  ^{:doc "Reimarin toimenpiteen komponenttikohtaiset tilat (Reimarin ACTCOMP_STATE_CDE)"}
  reimari-tp-komponentin-tilat
  {"1022540401" "Käytössa"
   "1022540402" "Poistettu"
   "1022540403" "Varastoon"})

(defn komponentin-tilakoodi->str [koodi] (get reimari-tp-komponentin-tilat koodi))
