(ns harja.domain.vesivaylat.turvalaite
  "Turvalaitteen tiedot"
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
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

(s/def ::hae-turvalaitteet-kartalle-kysely
  (s/keys :req []))

(s/def ::hae-turvalaitteet-kartalle-vastaus
  (s/nilable (s/coll-of ::turvalaite)))

(s/def ::hae-turvalaitteet-tekstilla-kysely
  (s/keys :req []))

(s/def ::hae-turvalaitteet-tekstilla-vastaus
  (s/nilable (s/coll-of ::turvalaite)))
