(ns harja.domain.vesivaylat.turvalaite
  "Turvalaitteen tiedot"
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.rel :as rel]
    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [
               [specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_turvalaite" ::reimari-turvalaite
   {"nro" ::r-nro
    "nimi" ::r-nimi
    "ryhma" ::r-ryhma}]
  ["vatu_turvalaite" ::turvalaite
   harja.domain.muokkaustiedot/muokkaustiedot
   ;; harja.domain.muokkaustiedot/poistettu?-sarake
   ])

(def perustiedot
  #{::turvalaitenro
    ::nimi
    ::koordinaatit
    ::sijainti
    ::tyyppi
    ::kiintea
    ::tila
    ::vah_pvm
    ::toimintatila
    ::rakenne
    ::navigointilaji
    ::valaistu
    ::omistaja
    ::turvalaitenro_aiempi
    ::paavayla
    ::vaylat
})

(s/def ::turvalaitenumerot
  (s/nilable (s/coll-of (s/nilable ::turvalaitenro))))

(s/def ::hae-turvalaitteet-kartalle-kysely
  (s/keys :req [] :opt-un [::turvalaitenumerot]))

(s/def ::hae-turvalaitteet-kartalle-vastaus
  (s/nilable (s/coll-of ::turvalaite)))

(s/def ::hae-turvalaitteet-tekstilla-kysely
  (s/keys :req []))

(s/def ::hae-turvalaitteet-tekstilla-vastaus
  (s/nilable (s/coll-of ::turvalaite)))
