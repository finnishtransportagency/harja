(ns harja.domain.vesivaylat.alus
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.urakka :as urakka]
    [harja.domain.organisaatio :as organisaatio]
    [harja.domain.muokkaustiedot :as m]
    [harja.geo :as geo]
    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_alus" ::reimari-alus
   {"tunnus" ::r-tunnus
    "nimi" ::r-nimi}]
  ["vv_alus" ::alus
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot
   {"urakoitsija" ::urakoitsija-id}]
  ["vv_alus_sijainti" ::aluksen-sijainti
   {"alus" ::alus-mmsi}]
  ["vv_alus_urakka" ::urakan-aluksen-kaytto
   {"urakka" ::urakka-id
    "alus" ::urakan-alus-mmsi
    "lisatiedot" ::urakan-aluksen-kayton-lisatiedot}
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot])

(def perustiedot #{::mmsi ::nimi ::lisatiedot})
(def viittaukset #{::urakoitsija-id})
(def sijaintitiedot #{::alus-mmsi ::sijainti ::aika})

(s/def ::sijainti ::geo/geometria)
(s/def ::kaytossa-urakassa? boolean?)
(s/def ::kaytossa-urakoissa? (s/coll-of integer?))

(s/def ::tallennettava-alus
  (s/keys :req [::mmsi]
          :opt [::nimi ::kaytossa-urakassa? ::lisatiedot ::urakan-aluksen-kayton-lisatiedot]))

(s/def ::hae-urakoitsijan-alukset-vastaus
  (s/coll-of ::tallennettava-alus))

(s/def ::hae-urakoitsijan-alukset-kysely
  (s/keys :req [::urakoitsija-id ::urakka/id]))

(s/def ::tallennettavat-alukset
  (s/coll-of ::tallennettava-alus))

(s/def ::tallenna-urakoitsijan-alukset-kysely
  (s/keys :req [::urakoitsija-id ::urakka/id ::tallennettavat-alukset]))

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

(defn alus-mmsilla [mmsi alukset]
  (first (filter #(= (::mmsi %) mmsi) alukset)))

(defn fmt-alus [alus]
  (str (::mmsi alus) " - " (::nimi alus)))
