(ns harja.domain.vesivaylat.vatu-turvalaite
  "Turvalaitteen tiedot"
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.rel :as rel]
    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

;;TODO: refaktoroi turvalaitteeseen liittyvä koodi käyttämään tätä luokkaa vesivaylat.turvalaite-luokan sijaan

(define-tables
  ["vatu_turvalaite" ::vatu-turvalaite])


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
    :vaylat
    :muokkaaja
    :muokattu
    :luoja
    :luotu})

(s/def ::hae-turvalaitteet-kartalle-kysely
  (s/keys :req []))

(s/def ::hae-turvalaitteet-kartalle-vastaus
  (s/nilable (s/coll-of ::turvalaite)))

(s/def ::hae-turvalaitteet-tekstilla-kysely
  (s/keys :req []))

(s/def ::hae-turvalaitteet-tekstilla-vastaus
  (s/nilable (s/coll-of ::turvalaite)))
