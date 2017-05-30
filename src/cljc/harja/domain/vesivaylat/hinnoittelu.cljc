(ns harja.domain.vesivaylat.hinnoittelu
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.domain.muokkaustiedot :as m]
    [harja.domain.vesivaylat.hinta :as h]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]
    [specql.rel :as rel]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["vv_hinnoittelu_toimenpide" ::hinnoittelu<->toimenpide
   {#?@(:clj [::toimenpiteet (rel/has-one
                               ::toimenpide-id
                               :harja.domain.toimenpide/toimenpide
                               :harja.domain.toimenpide/id)
              ::hinnoittelut (rel/has-one
                               ::hinnoittelu-id
                               ::hinnoittelu
                               ::id)])}]
  ["vv_hinnoittelu" ::hinnoittelu
   {"muokattu" ::m/muokattu
    "hintaryhma" ::hintaryhma?
    "muokkaaja" ::m/muokkaaja-id
    "luotu" ::m/luotu
    "luoja" ::m/luoja-id
    "poistettu" ::m/poistettu?
    "poistaja" ::m/poistaja-id
    #?@(:clj [::toimenpide-linkit (rel/has-many
                                    ::id
                                    ::hinnoittelu<->toimenpide
                                    ::hinnoittelu-id)
              ::hinnat (rel/has-many
                         ::id
                         ::h/hinta
                         ::h/hinnoittelu-id)])}])

(def perustiedot
  #{::nimi ::hintaryhma?})

(def hinnat
  #{[::hinnat h/perustiedot]})

(def hinnoittelutiedot
  (clojure.set/union perustiedot hinnat))

(def toimenpiteen-hinnoittelut
  #{[::hinnoittelut hinnoittelutiedot]})


