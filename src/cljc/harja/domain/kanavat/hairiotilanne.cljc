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
  ["kan_hairio_vikaluokka" ::hairiotilanne-vikaluokka (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_hairio" ::hairiotilanne
   {"urakka" ::urakka-id
    "sopimus" ::sopimus-id
    "kohde" ::kohde-id
    "ammattiliikenne_lkm" ::ammattiliikenne-lkm
    "huviliikenne_lkm" ::huviliikenne-lkm
    "paikallinen_kaytto" ::paikallinen-kaytto?
    "korjausaika_h" ::korjausaika-h
    "odotusaika_h" ::odotusaika-h
    "korjauksen_tila" ::korjauksen-tila}
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot
   {::kohde (specql.rel/has-one ::kohde-id
                                ::kkohde/kohde
                                ::kkohde/id)}])

(def perustiedot+muokkaustiedot
  #{::m/muokattu
    ::vikaluokka
    ::m/poistettu?
    ::huviliikenne-lkm
    ::korjaustoimenpide
    ::paikallinen-kaytto?
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

(def perustiedot+kanava+kohde
  (set/union perustiedot+muokkaustiedot
             #{[::kohde
                (set/union kkohde/perustiedot
                           #{[:harja.domain.kanavat.kanavan-kohde/kohteen-kanava
                              #{:harja.domain.kanavat.kanava/id
                                :harja.domain.kanavat.kanava/nimi}]})]}))

;; Palvelut

(s/def ::hae-hairiotilanteet-kysely (s/keys :req [::urakka-id ::sopimus-id
                                                  ::vikaluokka ::korjauksen-tila
                                                  ::paikallinen-kaytto?
                                                  ::odotusaika-h
                                                  ::korjausaika-h]
                                            :req-un [::aikavali]))
(s/def ::hae-hairiotilanteet-vastaus (s/coll-of ::hairiotilanne))

;; Apurit

;; Nämä on vectoreita, koska valintajärjestyksellä väliä
(def vikaluokat [:sahkotekninen_vika :konetekninen_vika :liikennevaurio])
(def vikaluokat+kaikki (vec (concat [nil] vikaluokat)))
(def korjauksen-tlat [:kesken :valmis])
(def korjauksen-tlat+kaikki (vec (concat [nil] korjauksen-tlat)))

(def fmt-vikaluokka
  {:sahkotekninen_vika "Sähkötekninen vika"
   :konetekninen_vika "Konetekninen vika"
   :liikennevaurio "Liikennevaurio"})

(def fmt-korjauksen-tila
  {:kesken "Kesken"
   :valmis "Valmis"})