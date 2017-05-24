(ns harja.domain.vesivaylat.hinta
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.domain.muokkaustiedot :as m]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]
    [specql.rel :as rel]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["vv_hinta" ::hinta
   {"muokattu" ::m/muokattu
    "muokkaaja" ::m/muokkaaja-id
    "luotu" ::m/luotu
    "luoja" ::m/luoja-id
    "poistettu" ::m/poistettu?
    "poistaja" ::m/poistaja-id
    #?@(:clj [::hinnoittelu (rel/has-one
                              ::hinnoittelu-id
                           :harja.domain.vesivaylat.hinnoittelu/hinnoittelu
                           :harja.domain.vesivaylat.hinnoittelu/id)])}])

(def perustiedot
  #{::otsikko
    ::maara
    ::yleiskustannuslisa})
