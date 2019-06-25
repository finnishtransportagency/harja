(ns harja.domain.vesivaylat.kiintio
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.set :as set]
            [specql.rel :as rel]
            [harja.id :refer [id-olemassa?]]
            [harja.domain.muokkaustiedot :as m]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            ])
            [harja.pvm :as pvm])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["vv_kiintio" ::kiintio
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistettu?-sarake
   harja.domain.muokkaustiedot/poistaja-sarake
   {::toimenpiteet (specql.rel/has-many ::id
                                        :harja.domain.vesivaylat.toimenpide/reimari-toimenpide
                                        :harja.domain.vesivaylat.toimenpide/kiintio-id)}])

(def perustiedot #{::id ::nimi ::kuvaus ::koko})

(def kiintion-toimenpiteet #{[::toimenpiteet #{:harja.domain.vesivaylat.toimenpide/id
                                               :harja.domain.vesivaylat.toimenpide/lisatieto
                                               :harja.domain.vesivaylat.toimenpide/suoritettu
                                               :harja.domain.vesivaylat.toimenpide/hintatyyppi
                                               [:harja.domain.vesivaylat.toimenpide/turvalaite
                                                #{:harja.domain.vesivaylat.turvalaite/turvalaitenro
                                                  :harja.domain.vesivaylat.turvalaite/nimi
                                                  :harja.domain.vesivaylat.turvalaite/tyyppi}]
                                               :harja.domain.vesivaylat.toimenpide/reimari-tyolaji
                                               :harja.domain.vesivaylat.toimenpide/reimari-tyoluokka
                                               :harja.domain.vesivaylat.toimenpide/reimari-toimenpidetyyppi}]})

(s/def ::idt (s/coll-of ::id))

(defn kiintio-idlla [kiintiot id]
  (first (filter #(= (::id %) id) kiintiot)))

(defn jarjesta-kiintiot [kiintiot]
  (filter
    (comp id-olemassa? ::id) ;; Poistetaan väliaikaiset kiintiöt
    (sort-by #(str/lower-case (::nimi %)) kiintiot)))

;; Palvelut

(s/def ::hae-kiintiot-kysely
  (s/keys :req [::urakka-id ::sopimus-id]))

(s/def ::hae-kiintiot-ja-toimenpiteet-vastaus
  (s/coll-of ::kiintio))

(s/def ::hae-kiintiot-vastaus
  (s/coll-of ::kiintio))

(s/def ::tallennettava-kiintio
  (s/keys :req [::nimi ::koko]
          :opt [::id ::kuvaus ::urakka-id ::sopimus-id ::m/poistettu? ::toimenpiteet]))

(s/def ::tallennettavat-kiintiot (s/coll-of ::tallennettava-kiintio))

(s/def ::tallenna-kiintiot-kysely
  (s/keys :req [::sopimus-id ::urakka-id ::tallennettavat-kiintiot]))

(s/def ::tallenna-kiintiot-vastaus ::hae-kiintiot-ja-toimenpiteet-vastaus)

(s/def ::tallenna-kiintiot-vastaus ::hae-kiintiot-ja-toimenpiteet-vastaus)

(s/def ::liita-toimenpiteet-kiintioon-kysely (s/keys :req [::id ::urakka-id
                                                           :harja.domain.vesivaylat.toimenpide/idt]))

;; Palauttaa liitettyjen toimenpiteiden id:t (samat jotka annettiin)
(s/def ::liita-toimenpiteet-kiintioon-vastaus (s/keys :req [:harja.domain.vesivaylat.toimenpide/idt]))

(s/def ::irrota-toimenpiteet-kiintiosta-kysely (s/keys :req [:harja.domain.vesivaylat.toimenpide/urakka-id
                                                            :harja.domain.vesivaylat.toimenpide/idt]))

;; Palauttaa liitettyjen toimenpiteiden id:t (samat jotka annettiin)
(s/def ::irrota-toimenpiteet-kiintiosta-vastaus (s/keys :req [:harja.domain.vesivaylat.toimenpide/idt]))
