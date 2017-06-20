(ns harja.domain.vesivaylat.kiintio
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.set :as set]
            [specql.rel :as rel]
            [harja.domain.muokkaustiedot :as m]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            [clojure.future :refer :all]])
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
                                                #{:harja.domain.vesivaylat.turvalaite/id
                                                  :harja.domain.vesivaylat.turvalaite/nimi
                                                  :harja.domain.vesivaylat.turvalaite/tyyppi
                                                  [:harja.domain.vesivaylat.turvalaite/vayla
                                                   #{:harja.domain.vesivaylat.vayla/id
                                                     :harja.domain.vesivaylat.vayla/nimi
                                                     :harja.domain.vesivaylat.vayla/tyyppi}]}]
                                               :harja.domain.vesivaylat.toimenpide/reimari-tyolaji
                                               :harja.domain.vesivaylat.toimenpide/reimari-tyoluokka
                                               :harja.domain.vesivaylat.toimenpide/reimari-toimenpidetyyppi}]})

(s/def ::hae-kiintiot-kysely
  (s/keys :req [::urakka-id ::sopimus-id]))

(s/def ::hae-kiintiot-vastaus
  (s/coll-of ::kiintio))

(s/def ::tallennettava-kiintio
  (s/keys :req [::nimi ::koko]
          :opt [::kuvaus ::urakka-id ::sopimus-id ::m/poistettu? ::toimenpiteet ::m/muokkaaja-id
                ::m/muokattu ::m/luoja-id ::m/luotu ::m/poistaja-id]))

(s/def ::tallennettavat-kiintiot (s/coll-of ::tallennettava-kiintio))

(s/def ::tallenna-kiintiot-kysely
  (s/keys :req [::sopimus-id ::urakka-id ::tallennettavat-kiintiot]))

(s/def ::tallenna-kiintiot-vastaus ::hae-kiintiot-vastaus)
