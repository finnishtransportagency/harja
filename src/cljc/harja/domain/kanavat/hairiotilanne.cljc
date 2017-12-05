(ns harja.domain.kanavat.hairiotilanne
  (:require
    [clojure.spec.alpha :as s]
    [clojure.set :as set]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]]
        :cljs [[specql.impl.registry]])

    [harja.domain.muokkaustiedot :as m]
    [harja.domain.kanavat.kohde :as kohde]
    [harja.domain.kanavat.kohteenosa :as kohteenosa]
    [harja.domain.urakka :as ur]
    [harja.domain.kayttaja :as kayttaja]
    [harja.domain.muokkaustiedot :as muokkaustiedot])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kan_hairio_korjauksen_tila" ::hairiotilanne-korjauksen-tila (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_hairio_vikaluokka" ::hairiotilanne-vikaluokka (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_hairio" ::hairiotilanne
   {"urakka" ::urakka-id
    "sopimus" ::sopimus-id
    "ammattiliikenne_lkm" ::ammattiliikenne-lkm
    "huviliikenne_lkm" ::huviliikenne-lkm
    "paikallinen_kaytto" ::paikallinen-kaytto?
    "korjausaika_h" ::korjausaika-h
    "odotusaika_h" ::odotusaika-h
    "korjauksen_tila" ::korjauksen-tila
    "kuittaaja" ::kuittaaja-id
    ::kuittaaja (specql.rel/has-one ::kuittaaja-id
                                    :harja.domain.kayttaja/kayttaja
                                    :harja.domain.kayttaja/id)}
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot
   {::kohde (specql.rel/has-one ::kohde-id
                                ::kohde/kohde
                                ::kohde/id)
    ::kohteenosa (specql.rel/has-one ::kohteenosa-id
                                     :harja.domain.kanavat.kohteenosa/kohteenosa
                                     :harja.domain.kanavat.kohteenosa/id)}])

(def perustiedot
  #{::vikaluokka
    ::huviliikenne-lkm
    ::korjaustoimenpide
    ::paikallinen-kaytto?
    ::pvm
    ::korjausaika-h
    ::odotusaika-h
    ::syy
    ::id
    ::korjauksen-tila
    ::ammattiliikenne-lkm})

(def viittaus-idt
  #{::urakka-id
    ::sopimus-id
    ::kohde-id
    ::kohteenosa-id})

(def muokkaustiedot muokkaustiedot/muokkauskentat)

(def kuittaajan-tiedot
  #{[::kuittaaja kayttaja/perustiedot]})

(def kohteenosan-tiedot #{[::kohteenosa kohteenosa/perustiedot]})

(def kohteen-tiedot #{[::kohde (set/union kohde/perustiedot)]})

;; Palvelut

(s/def ::hae-hairiotilanteet-kysely (s/keys :req [::urakka-id]
                                            ;; Nämä eivät suoraan mappaudu domainin kanta-arvoihin, koska
                                            ;; voi olla myös kaikki-valinta (nil), lisäksi
                                            ;; numerovälit esitetään vectoreina
                                            :opt-un [::haku-aikavali
                                                     ::haku-korjausaika-h
                                                     ::haku-odotusaika-h
                                                     ::haku-vikaluokka
                                                     ::haku-sopimus-id
                                                     ::haku-korjauksen-tila
                                                     ::haku-paikallinen-kaytto?]))
(s/def ::hae-hairiotilanteet-vastaus (s/coll-of ::hairiotilanne))

(s/def ::tallenna-hairiotilanne-kutsu
  (s/keys :req [::hae-hairiotilanteet-kysely
                ::hairiotilanne]))

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