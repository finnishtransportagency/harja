(ns harja.domain.kanavat.kanavan-huoltokohde
  (:require
    [clojure.spec.alpha :as s]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kan_huoltokohde" ::huoltokohde])

(def perustiedot
  #{::id
    ::nimi})