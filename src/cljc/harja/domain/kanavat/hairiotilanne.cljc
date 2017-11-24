(ns harja.domain.kanavat.hairiotilanne
  (:require
    [clojure.spec.alpha :as s]
    [clojure.set :as set]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]])

    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.domain.kanavat.kanavan-kohde :as kanavan-kohde]
    [harja.domain.kayttaja :as kayttaja]
    [harja.domain.vesivaylat.materiaali :as materiaali])
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
    "korjauksen_tila" ::korjauksen-tila
    "kuittaaja" ::kuittaaja-id
    ::kuittaaja (specql.rel/has-one ::kuittaaja-id
                                    :harja.domain.kayttaja/kayttaja
                                    :harja.domain.kayttaja/id)
    ::materiaalit (specql.rel/has-many ::id ::materiaali/materiaali ::materiaali/hairiotilanne)}
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot
   {::kohde (specql.rel/has-one ::kohde-id
                                ::kanavan-kohde/kohde
                                ::kanavan-kohde/id)}])

(def kuittaajan-tiedot
  #{[::kuittaaja
     #{::kayttaja/id
       ::kayttaja/etunimi
       ::kayttaja/sukunimi
       ::kayttaja/kayttajanimi
       ::kayttaja/sahkoposti
       ::kayttaja/puhelin}]})

(def materiaalien-tiedot
  ;; todo: tämä ilmeisesti ei toimi many-to-one suhteessa
  #{[::materiaalit
     #{::materiaali/id
       ::materiaali/nimi
       ::materiaali/maara
       ::materiaali/pvm}]})

(def perustiedot+muokkaustiedot
  #{::muokkaustiedot/muokattu
    ::vikaluokka
    ::muokkaustiedot/poistettu?
    ::huviliikenne-lkm
    ::korjaustoimenpide
    ::paikallinen-kaytto?
    ::pvm
    ::urakka-id
    ::muokkaustiedot/muokkaaja-id
    ::korjausaika-h
    ::muokkaustiedot/luotu
    ::odotusaika-h
    ::syy
    ::muokkaustiedot/luoja-id
    ::id
    ::korjauksen-tila
    ::sopimus-id
    ::ammattiliikenne-lkm})

(def perustiedot+kanava+kohde
  (set/union perustiedot+muokkaustiedot
             kuittaajan-tiedot
             materiaalien-tiedot
             #{[::kohde
                (set/union kanavan-kohde/perustiedot
                           kanavan-kohde/kohteen-kanavatiedot)]}))

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