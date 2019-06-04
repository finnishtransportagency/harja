(ns harja.domain.kanavat.kohdekokonaisuus
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set :as set]
    [specql.rel :as rel]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [[specql.impl.registry]])

    [harja.domain.muokkaustiedot :as m]
    [harja.domain.kanavat.kohteenosa :as osa]
    [harja.domain.urakka :as ur])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kan_kohdekokonaisuus" ::kohdekokonaisuus
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::kohteet (specql.rel/has-many ::id
                                   :harja.domain.kanavat.kohde/kohde
                                   :harja.domain.kanavat.kohde/kohdekokonaisuus-id)}])

(def perustiedot
  #{::id
    ::nimi})

(def perustiedot+sijainti
  (set/union perustiedot #{::sijainti}))

(def kohteet
  #{[::kohteet #{:harja.domain.kanavat.kohde/id
                 :harja.domain.kanavat.kohde/nimi
                 :harja.domain.kanavat.kohde/jarjestys
                 [:harja.domain.kanavat.kohde/kohteenosat osa/perustiedot]}]})

(def kohteet-sijainteineen
  #{[::kohteet #{:harja.domain.kanavat.kohde/id
                 :harja.domain.kanavat.kohde/nimi
                 :harja.domain.kanavat.kohde/sijainti}]})

;; Palvelut

(s/def ::hakuteksti string?)

(s/def ::hae-kohdekokonaisuudet-ja-kohteet-vastaus
  (s/coll-of (s/keys :req [::id ::nimi ::kohteet])))

(s/def ::lisaa-kohdekokonaisuudelle-kohteita-kysely
  (s/coll-of (s/keys :req [:harja.domain.kanavat.kohde/kohdekokonaisuus-id
                           :harja.domain.kanavat.kohde/id]
                     :opt [:harja.domain.kanavat.kohde/nimi
                           ::m/poistettu?])))

(s/def ::lisaa-kohdekokonaisuudelle-kohteita-vastaus ::hae-kohdekokonaisuudet-ja-kohteet-vastaus)

(s/def ::tallenna-kohdekokonaisuudet-kysely
  (s/coll-of (s/keys :req [::nimi]
                     :opt [::id])))

(s/def ::tallenna-kohdekokonaisuudet-vastaus ::hae-kohdekokonaisuudet-ja-kohteet-vastaus)

(s/def ::poista-kohde-kysely (s/keys :req-un [::kohde-id]))

(s/def ::hae-urakan-kohteet-kysely (s/keys :req [::ur/id]))
(s/def ::hae-urakan-kohteet-vastaus (s/coll-of (s/keys :req [:harja.domain.kanavat.kohde/id
                                                             :harja.domain.kanavat.kohde/kohdekokonaisuus]
                                                       :opt [:harja.domain.kanavat.kohde/nimi])))
