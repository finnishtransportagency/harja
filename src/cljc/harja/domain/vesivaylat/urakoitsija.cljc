(ns harja.domain.vesivaylat.urakoitsija
  (:require
    [clojure.spec :as s]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]
              [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_urakoitsija" ::urakoitsija])
