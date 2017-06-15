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
   (merge
     m/muokkaustiedot
     m/poistettu?-sarake
     m/poistaja-sarake
     {::toimenpiteet (specql.rel/has-many ::id
                                          :harja.domain.vesivaylat.toimenpide/toimenpide
                                          ::harja.domain.vesivaylat.toimenpide/kiintio-id)})])

(def perustiedot #{::id ::nimi ::kuvaus ::koko})

(def kiintion-toimenpiteet #{[::toimenpiteet #{:harja.domain.vesivaylat.toimenpide/id
                                               :harja.domain.vesivaylat.toimenpide/lisatieto
                                               :harja.domain.vesivaylat.toimenpide/suoritettu
                                               :harja.domain.vesivaylat.toimenpide/hintatieto
                                               :harja.domain.vesivaylat.toimenpide/reimari-tyolaji
                                               :harja.domain.vesivaylat.toimenpide/reimari-tyoluokka
                                               :harja.domain.vesivaylat.toimenpide/reimari-toimenpidetyyppi}]})
