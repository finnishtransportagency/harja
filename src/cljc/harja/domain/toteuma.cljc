(ns harja.domain.toteuma
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.muokkaustiedot :as m]

    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["toteuma" ::toteuma
   {"muokattu" ::m/muokattu
    "muokkaaja" ::m/muokkaaja-id
    "luotu" ::m/luotu
    "luoja" ::m/luoja-id
    "poistettu" ::m/poistettu?}])
