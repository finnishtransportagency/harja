(ns harja.domain.kanavat.kanavan-huoltokohde
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kan_huoltokohde" ::huoltokohde])

(def perustiedot
  #{::id
    ::nimi})

(s/def ::hae-huoltokohteet-vastaus (s/coll-of ::huoltokohde))
