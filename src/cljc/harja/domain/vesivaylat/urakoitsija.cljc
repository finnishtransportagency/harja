(ns harja.domain.vesivaylat.urakoitsija
  (:require
    [clojure.spec.alpha :as s]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]
              ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_urakoitsija" ::urakoitsija])
