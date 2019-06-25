(ns harja.domain.vesivaylat.hinnoittelu
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.domain.muokkaustiedot :as m]
    [harja.domain.vesivaylat.hinta :as hinta]
    [harja.domain.urakka :as ur]
    [harja.domain.vesivaylat.tyo :as tyo]
    [harja.domain.vesivaylat.kommentti :as kommentti]
    [clojure.set :as set]
    [specql.rel :as rel]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [[specql.impl.registry]])
    [harja.pvm :as pvm])
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
               ::hinta/hinnoittelu-id)
    ::tyot (specql.rel/has-many
             ::id
             ::tyo/tyo
             ::tyo/hinnoittelu-id)}])

(def perustiedot
  #{::nimi
    ::hintaryhma?
    ::id})

(def viittaus-idt
  #{::urakka-id})

(def metatiedot m/muokkauskentat)

(def hinnat
  #{[::hinnat (set/union hinta/perustiedot hinta/metatiedot)]})

(def tyot
  #{[::tyot (set/union tyo/perustiedot tyo/viittaus-idt tyo/metatiedot)]})

(def hinnoittelutiedot
  (set/union perustiedot metatiedot hinnat))

(def toimenpiteen-hinnoittelut
  #{[::hinnoittelut hinnoittelutiedot]})

(s/def ::idt (s/coll-of ::id))
(s/def ::tyhja? boolean?) ;; ;; Ei sisällä lainkaan toimenpiteitä kannassa

;; Apurit

(defn hinnoittelu-idlla [hinnoittelut id]
  (first (filter #(= (::id %) id) hinnoittelut)))

(defn hinnoittelut-ilman [hinnoittelut idt]
  (filter (comp not #(idt (::id %))) hinnoittelut))

(defn jarjesta-hintaryhmat [hintaryhmat]
  (sort-by #(str/lower-case (::nimi %)) hintaryhmat))

(defn hintaryhman-nimi [hintaryhma]
  (::nimi hintaryhma))

;; Palvelut

(s/def ::laskutus-pvm #?(:clj (s/nilable (comp pvm/pvm? pvm/joda-timeksi))
                         :cljs (s/nilable pvm/pvm?)))
(s/def ::laskutettu? boolean?)

(s/def ::hae-hintaryhmat-kysely
  (s/keys
    :req [::ur/id]))

(s/def ::hae-hintaryhmat-vastaus
  (s/coll-of
    (s/keys :req [::id ::nimi ::hintaryhma? ::tyhja? ::laskutus-pvm ::laskutettu?])))

(s/def ::luo-hinnoittelu-kysely
  (s/keys
    :req [::nimi ::ur/id]))

(s/def ::luo-hinnoittelu-vastaus
  (s/keys
    :req [::id ::nimi ::hintaryhma? ::tyhja?]))

(s/def ::liita-toimenpiteet-hinnotteluun-kysely
  (s/keys
    :req [:harja.domain.vesivaylat.toimenpide/idt
          ::id
          ::ur/id]))

(s/def ::tallennettava-hinta (s/keys :req [::hinta/summa ::hinta/otsikko ::hinta/yleiskustannuslisa ::hinta/ryhma]
                                     :opt [::hinta/id ::m/poistettu?]))

(s/def ::tallennettavat-hinnat
  (s/coll-of ::tallennettava-hinta))

(s/def ::tallennettava-tyo (s/keys :req [::tyo/toimenpidekoodi-id ::tyo/maara]
                                   :opt [::tyo/id ::m/poistettu?]))

(s/def ::tallennettavat-tyot (s/coll-of ::tallennettava-tyo))

(s/def ::tallenna-hintaryhmalle-hinta-kysely
  (s/keys
    :req [::ur/id ::id ::tallennettavat-hinnat]))

(s/def ::tallenna-hintaryhmalle-hinta-vastaus ::hae-hintaryhmat-vastaus)

(s/def ::tallenna-vv-toimenpiteen-hinta-kysely
  (s/keys
    :req [:harja.domain.vesivaylat.toimenpide/urakka-id
          :harja.domain.vesivaylat.toimenpide/id
          ;; Könttähinnat
          ::tallennettavat-hinnat
          ;; Yksittäiset työt sekä työhinnat
          ::tallennettavat-tyot]))

(s/def ::tallenna-vv-toimenpiteen-hinta-vastaus ::hinnoittelu)

(s/def ::poista-tyhjat-hinnoittelut-kysely
  (s/keys :req [::urakka-id ::idt]))

;; Poistetut hintaryhmät-idt
(s/def ::poista-tyhjat-hinnoittelut-vastaus (s/keys :req [::idt]))

(s/def ::tallenna-hinnoittelun-kommentti-kysely (s/keys :req [::urakka-id
                                                              ::kommentti/tila
                                                              ::kommentti/kommentti
                                                              ::id]))
(s/def ::tallenna-hinnoittelun-kommentti-vastaus (s/keys :req []))
