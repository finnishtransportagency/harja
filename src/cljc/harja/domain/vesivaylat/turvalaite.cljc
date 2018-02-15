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

;; turvalaitetauluja on 3:
;; - reimari_turvalaite - reimarista tuleva tieto turvalaitteiden urakka-alueista
;; - vv_turvalaite (= alk/ava-tiedot ) - ei enää käytössä, entinen turvalaitedata
;; - vatu_turvalaite - varsinainen turvalaitedata
;;

(define-tables
  ["reimari_turvalaite" ::reimari-turvalaite
   {"nro" ::r-nro
    "nimi" ::r-nimi
    "ryhma" ::r-ryhma}]
  ["vatu_turvalaite" ::turvalaite
   harja.domain.muokkaustiedot/muokkaustiedot
   ;; harja.domain.muokkaustiedot/poistettu?-sarake
   ])

;; todo: poistuvat sarakkeee vv_turvalaite -> vatu_turvalaite -muutoksen myötä
;; id
;; arvot (json)
;; kiintea - mikä tämä oli, tarvitanako vielä, löytyykö vatu-tiedoista?
;; poistettu

;; (def perustiedot
;;   #{::turvalaitenro
;;     ::nimi
;;     ::tyyppi
;;     ::kiintea
;;     ::vaylat})


(def perustiedot
  #{::turvalaitenro
    ::nimi
    ::sijainti
    ;; ::sijaintikuvaus ;; ei kantataulussa
    ::tyyppi
    ::tarkenne
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

(s/def ::hae-turvalaitteet-kartalle-kysely
  (s/keys :req []))

(s/def ::hae-turvalaitteet-kartalle-vastaus
  (s/nilable (s/coll-of ::turvalaite)))

(s/def ::hae-turvalaitteet-tekstilla-kysely
  (s/keys :req []))

(s/def ::hae-turvalaitteet-tekstilla-vastaus
  (s/nilable (s/coll-of ::turvalaite)))
