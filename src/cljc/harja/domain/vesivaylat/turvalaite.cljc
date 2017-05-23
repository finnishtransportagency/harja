(ns harja.domain.vesivaylat.turvalaite
  "Turvalaitteen tiedot"
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [harja.domain.vesivaylat.vayla :as v]

    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]
    [specql.rel :as rel]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_turvalaite" ::reimari-turvalaite
   {"nro" ::r-nro
    "nimi" ::r-nimi
    "ryhma" ::r-ryhma}]
  ["vv_turvalaite" ::turvalaite
   {"vayla" ::vayla-id
    #?@(:clj [::vayla (rel/has-one ::vayla-id ::v/vayla ::v/id)])}])

(def tyypit (s/describe ::tyyppi))

(def perustiedot
  #{::id
    ::nimi
    ::tyyppi
    [::vayla v/perustiedot]})
