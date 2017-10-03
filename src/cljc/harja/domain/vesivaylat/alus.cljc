(ns harja.domain.vesivaylat.alus
  (:require
    [clojure.spec.alpha :as s]
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