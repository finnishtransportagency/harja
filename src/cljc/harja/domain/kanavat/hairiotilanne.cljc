(ns harja.domain.kanavat.hairiotilanne
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set :as set]
    [specql.rel :as rel]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]])

    [harja.domain.muokkaustiedot :as m]
    [harja.domain.kanavat.kanavan-kohde :as kkohde]
    [harja.domain.urakka :as ur])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kan_hairio_korjauksen_tila" ::hairiotilanne-korjauksen-tila (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_hairio" ::hairiotilanne
   {"urakka" ::urakka-id
    "sopimus" ::sopimus-id
    "kohde" ::kohde-id
    "ammattiliikenne_lkm" ::ammattiliikenne-lkm
    "huviliikenne_lkm" ::huviliikenne-lkm
    "paikallinen_kaytto" ::paikallinen-kaytto
    "korjausaika_h" ::korjausaika-h
    "odotusaika_h" ::odotusaika-h
    "korjauksen_tila" ::korjauksen-tila}
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot
   {::kohde (specql.rel/has-one ::id
                                ::kkohde/kohde
                                ::kkohde/id)}])

(def perustiedot+muokkaustiedot
  #{::m/muokattu
    ::vikaluokka
    ::m/poistettu?
    ::huviliikenne-lkm
    ::korjaustoimenpide
    ::paikallinen-kaytto
    ::pvm
    ::urakka-id
    ::m/muokkaaja-id
    ::korjausaika-h
    ::m/luotu
    ::odotusaika-h
    ::syy
    ::m/luoja-id
    ::id
    ::korjauksen-tila
    ::sopimus-id
    ::ammattiliikenne-lkm})

(def kohde (set/union #{[::kohde kkohde/perustiedot]}
                      ;kkohde/kohteen-kanava
                      ))

;; Palvelut

(s/def ::hae-hairiotilanteet-kysely (s/keys :req [::urakka-id ::sopimus-id]))
(s/def ::hae-hairiotilanteet-vastaus (s/coll-of ::hairiotilanne))