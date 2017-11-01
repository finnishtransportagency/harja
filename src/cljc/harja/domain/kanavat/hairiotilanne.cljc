(ns harja.domain.kanavat.hairiotilanne
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set]
    [specql.rel :as rel]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]])

    [harja.domain.muokkaustiedot :as m]
    [harja.domain.urakka :as ur])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))


;; TODO define-tables

(define-tables
  ["kan_hairio" ::hairiotilanne
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot])

(def kaikki-sarakkeet
  #{})

;; Palvelut

;; TODO specit palveluille