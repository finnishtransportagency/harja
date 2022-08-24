(ns harja.domain.alueurakka-domain
  (:require [clojure.spec.alpha :as s]
            [specql.transform :as tx]
            [specql.rel]
            [harja.kyselyt.specql]
            #?(:clj [harja.kyselyt.specql-db :refer [define-tables]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))
(define-tables
  ["alueurakka" ::alueurakka])
