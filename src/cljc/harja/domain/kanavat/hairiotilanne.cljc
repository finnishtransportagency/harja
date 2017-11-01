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
  ["kan_hairio_korjauksen_tila" ::hairiotilanne-korjauksen-tila (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_hairio" ::hairiotilanne
   {"ammattiliikenne_lkm" ::ammattiliikenne-lkm
    "huviliikenne_lkm" ::huviliikenne-lkm
    "paikallinen_kaytto" ::paikallinen-kaytto
    "korjausaika_h" ::korjausaika-h
    "odotusaika_h" ::odotusaika-h
    "korjauksen_tila" ::korjauksen-tila}
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot])

(def kaikki-sarakkeet
  #{::m/muokattu
    ::vikaluokka
    ::m/poistettu?
    ::huviliikenne-lkm
    ::korjaustoimenpide
    ::paikallinen-kaytto
    ::pvm
    ::urakka
    ::m/muokkaaja-id
    ::korjausaika-h
    ::m/luotu
    ::odotusaika-h
    ::syy
    ::m/luoja-id
    ::kohde
    ::id
    ::korjauksen-tila
    ::sopimus
    ::ammattiliikenne-lkm})

;; Palvelut

;; TODO specit palveluille