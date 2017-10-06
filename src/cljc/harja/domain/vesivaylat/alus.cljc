(ns harja.domain.vesivaylat.alus
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.urakka :as urakka]
    [harja.domain.organisaatio :as organisaatio]
    [harja.domain.muokkaustiedot :as m]
    [harja.geo :as geo]
    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_alus" ::reimari-alus
   {"tunnus" ::r-tunnus
    "nimi" ::r-nimi}]
  ["vv_alus" ::alus
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot]
  ["vv_alus_sijainti" ::aluksen-sijainti
   {"alus" ::alus-mmsi}])

(def perustiedot #{::mmsi ::nimi ::lisatiedot})
(def sijaintitiedot #{::alus-mmsi ::sijainti ::aika})



(s/def ::sijainti ::geo/geometria)

(s/def ::hae-urakan-alukset-kysely
  (s/keys :req [::urakka/id]))

(s/def ::hae-urakan-alukset-vastaus
  (s/coll-of ::alus))

(s/def ::hae-urakoitsijan-alukset-kysely
  (s/keys :req [::organisaatio/id]))

(s/def ::hae-urakoitsijan-alukset-vastaus
  (s/coll-of ::alus))

(s/def ::hae-kaikki-alukset-kysely
  (s/keys :req []))

(s/def ::hae-kaikki-alukset-vastaus
  (s/coll-of ::alus))

(s/def ::hae-alusten-reitit-pisteineen-kysely
  (s/keys :opt-un [::alku ::loppu ::laivat]))

(s/def ::pistetieto (s/keys :req [::aika ::sijainti]))

(s/def ::pisteet (s/coll-of ::pistetieto))

(s/def ::hae-alusten-reitit-pisteineen-vastaus
  (s/coll-of (s/keys :req [::pisteet ::sijainti ::alus-mmsi])))

(s/def ::hae-alusten-reitit-kysely
  (s/keys :opt-un [::alku ::loppu ::laivat]))

(s/def ::hae-alusten-reitit-vastaus
  (s/coll-of ::aluksen-sijainti))