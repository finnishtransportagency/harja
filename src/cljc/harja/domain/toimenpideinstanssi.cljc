(ns harja.domain.toimenpideinstanssi
  (:require [clojure.spec.alpha :as s]
            [specql.transform]
            [specql.rel]
            #?(:clj [harja.kyselyt.specql-db :refer [define-tables]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["toimenpideinstanssi" ::toimenpideinstanssi])
