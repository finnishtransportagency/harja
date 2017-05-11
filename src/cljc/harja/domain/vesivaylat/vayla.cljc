(ns harja.domain.vesivaylat.vayla
  "Väylän tiedot"
  (:require
    [clojure.spec :as s]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]
              [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_vayla" ::reimari-vayla]
  ["vv_vayla" ::vayla])