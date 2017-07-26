(ns harja.domain.vesivaylat.turvalaite
  "Turvalaitteen tiedot"
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.rel :as rel]

    [harja.domain.vesivaylat.vayla :as v]
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
  ["vv_turvalaite" ::turvalaite
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistettu?-sarake])

(def tyypit (s/describe ::tyyppi))

(def perustiedot
  #{::id
    ::nimi
    ::tyyppi
    ::turvalaitenro
    ::kiintea
    ::vaylat})

(s/def ::hae-turvalaitteet-kysely
  (s/keys :req []))

(s/def ::hae-turvalaitteet-vastaus
  (s/coll-of ::turvalaite))
