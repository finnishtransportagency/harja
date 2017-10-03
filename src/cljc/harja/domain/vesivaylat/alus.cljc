(ns harja.domain.vesivaylat.alus
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.urakka :as urakka]
    [harja.domain.organisaatio :as organisaatio]
    [harja.domain.muokkaustiedot :as m]
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