(ns harja.domain.vesivaylat.hinnoittelu
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.domain.muokkaustiedot :as m]
    [harja.domain.vesivaylat.hinta :as hinta]
    [harja.domain.urakka :as ur]
    [specql.rel :as rel]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["vv_hinnoittelu_toimenpide" ::hinnoittelu<->toimenpide
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::toimenpiteet (specql.rel/has-one
                     ::toimenpide-id
                     :harja.domain.toimenpide/toimenpide
                     :harja.domain.toimenpide/id)
    ::hinnoittelut (specql.rel/has-one
                     ::hinnoittelu-id
                     ::hinnoittelu
                     ::id)}]
  ["vv_hinnoittelu" ::hinnoittelu
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {"hintaryhma" ::hintaryhma?
    ::toimenpide-linkit (specql.rel/has-many
                          ::id
                          ::hinnoittelu<->toimenpide
                          ::hinnoittelu-id)
    ::hinnat (specql.rel/has-many
               ::id
               ::hinta/hinta
               ::hinta/hinnoittelu-id)}])

(def perustiedot
  #{::nimi
    ::hintaryhma?
    ::id})

(def metatiedot m/muokkauskentat)

(def hinnat
  #{[::hinnat hinta/perustiedot hinta/metatiedot]})

(def hinnoittelutiedot
  (clojure.set/union perustiedot hinnat))

(def toimenpiteen-hinnoittelut
  #{[::hinnoittelut hinnoittelutiedot]})

(def hinnoittelun-toimenpiteet
  #{[::toimenpide-linkit
     #{[::toimenpiteet
        #{:harja.domain.vesivaylat.toimenpide/id
          :harja.domain.vesivaylat.toimenpide/urakka-id
          :harja.domain.vesivaylat.toimenpide/hintatyyppi
          :harja.domain.vesivaylat.toimenpide/lisatyo?}]}]})

(s/def ::hae-hinnoittelut-kysely
  (s/keys
    :req [::ur/id]))

(s/def ::hae-hinnoittelut-vastaus
  (s/coll-of
    (s/keys :req [::id ::nimi ::hintaryhma?])))

(s/def ::luo-hinnoittelu-kysely
  (s/keys
    :req [::nimi ::ur/id]))

(s/def ::luo-hinnoittelu-vastaus
  (s/keys
    :req [::nimi ::hintaryhma? ::id]))

(s/def ::liita-toimenpiteet-hinnotteluun-kysely
  (s/keys
    :req [:harja.domain.vesivaylat.toimenpide/idt
          ::id
          ::ur/id]))

(s/def ::hintaelementit
  (s/coll-of
    (s/keys :req [::hinta/maara ::hinta/otsikko ::hinta/yleiskustannuslisa])))

(s/def ::tallenna-hintaryhmalle-hinta-kysely
  (s/keys
    :req [::id ::hinnat ::ur/id]))

(s/def ::tallenna-hintaryhmalle-hinta-vastaus ::hinnoittelu)

(s/def ::tallenna-toimenpiteelle-hinta-kysely
  (s/keys
    :req [:harja.domain.vesivaylat.toimenpide/id
          ::hintaelementit
          :harja.domain.vesivaylat.toimenpide/id]))

(s/def ::tallenna-toimenpiteelle-hinta-vastaus ::hinnoittelu)