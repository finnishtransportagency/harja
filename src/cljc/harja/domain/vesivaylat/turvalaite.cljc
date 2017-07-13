(ns harja.domain.vesivaylat.turvalaite
  "Turvalaitteen tiedot"
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [harja.domain.vesivaylat.vayla :as v]
    [specql.rel :as rel]

    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_turvalaite" ::reimari-turvalaite
   {"nro" ::r-nro
    "nimi" ::r-nimi
    "ryhma" ::r-ryhma}]
  ["vv_turvalaite" ::turvalaite])

(def tyypit (s/describe ::tyyppi))

(def perustiedot
  #{::id
    ::nimi
    ::tyyppi
    ::turvalaitenro
    ::kiintea
    ::vaylat})
