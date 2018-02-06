(ns harja.domain.paikkaus
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.muokkaustiedot :as muokkautiedot]
    [harja.kyselyt.specql :as harja-specql]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]]))
  #?(:clj
     (:import (org.postgis PGgeometry))))

(define-tables
  ["paikkauskohde" ::paikkauskohde]
  ["paikkaustoteuma" ::paikkaustoteuma
   {"luoja-id" ::muokkautiedot/luoja-id
    "luotu" ::muokkautiedot/luotu
    "muokaaja-id" ::muokkautiedot/muokkaaja-id
    "muokattu" ::muokkautiedot/muokattu
    "poistettu-id" ::muokkautiedot/poistettu}]
  ["paikkauksen_tienkohta" ::paikkauksen-tienkohta]
  ["paikkauksen_materiaalit" ::paikkauksen-materiaalit])
