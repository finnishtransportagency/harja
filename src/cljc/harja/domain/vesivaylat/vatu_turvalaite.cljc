(ns harja.domain.vesivaylat.vatu-turvalaite
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

;;TODO; muuta koodi käyttämään tätä luokkaa

(define-tables
  ["turvalaitenro" ::turvalaitenro
   "nimi" ::nimi
   "sijainti" ::sijainti
   "sijaintikuvaus" ::sijaintikuvaus
   "tyyppi" ::tyyppi
   "tarkenne" ::tarkenne
   "tila" ::tila
   "vah_pvm" ::vah_pvm
   "toimintatila" ::toimintatila
   "rakenne" ::rakenne
   "navigointilaji" ::navigointilaji
   "valaistu" ::valaistu
   "omistaja" ::omistaja
   "turvalaitenro_aiempi" ::turvalaitenro_aiempi
   "paavayla" ::paavayla
   "vaylat" ::vaylat
   [harja.domain.muokkaustiedot/muokkaustiedot]])

(def tyypit (s/describe ::tyyppi))

(def perustiedot
  #{:turvalaitenro
    :nimi
    :sijainti
    :sijaintikuvaus
    :tyyppi
    :tarkenne
    :tila
    :vah_pvm
    :toimintatila
    :rakenne
    :navigointilaji
    :valaistu
    :omistaja
    :turvalaitenro_aiempi
    :paavayla
    :vaylat})

(s/def ::hae-turvalaitteet-kartalle-kysely
  (s/keys :req []))

(s/def ::hae-turvalaitteet-kartalle-vastaus
  (s/nilable (s/coll-of ::turvalaite)))

(s/def ::hae-turvalaitteet-tekstilla-kysely
  (s/keys :req []))

(s/def ::hae-turvalaitteet-tekstilla-vastaus
  (s/nilable (s/coll-of ::turvalaite)))
