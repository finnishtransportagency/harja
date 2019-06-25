(ns harja.domain.kanavat.kohde
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
    [harja.domain.kanavat.kohdekokonaisuus :as kok]
    [harja.domain.kanavat.kohteenosa :as osa])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kohteen_tyyppi" ::kohteen-tyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_kohde" ::kohde
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::kohdekokonaisuus (specql.rel/has-one ::kohdekokonaisuus-id
                                           :harja.domain.kanavat.kohdekokonaisuus/kohdekokonaisuus
                                           :harja.domain.kanavat.kohdekokonaisuus/id)
    ::ylos (specql.rel/has-one ::ylos-id
                               ::kohde
                               ::id)
    ::alas (specql.rel/has-one ::alas-id
                               ::kohde
                               ::id)
    ::kohteenosat (specql.rel/has-many ::id
                                       :harja.domain.kanavat.kohteenosa/kohteenosa
                                       :harja.domain.kanavat.kohteenosa/kohde-id)}]
  ["kan_kohde_urakka" ::kohde<->urakka
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::linkin-urakka (specql.rel/has-one ::urakka-id
                                        :harja.domain.urakka/urakka
                                        :harja.domain.urakka/id)
    ::linkin-kohde (specql.rel/has-one ::kohde-id
                                       ::kohde
                                       ::id)}])

(def perustiedot
  #{::id
    ::nimi
    ::sijainti
    ::jarjestys})

(def kohde-ylos
  #{[::ylos perustiedot]})

(def kohde-alas
  #{[::alas perustiedot]})

(def kohteenosat
  #{[::kohteenosat osa/perustiedot]})

(def metatiedot m/muokkauskentat)

(def perustiedot-ja-sijainti (conj perustiedot ::sijainti))

(def kohteen-urakkatiedot #{[::linkin-urakka #{:harja.domain.urakka/id :harja.domain.urakka/nimi}]})

(def kohteen-kohdekokonaisuus #{[::kohdekokonaisuus kok/perustiedot]})

;; Domain-funktiot

(defn fmt-kohteen-nimi
  [kohde]
  (::nimi kohde))

(defn fmt-kohde-ja-osa-nimi
  [kohde osa]
  (str
    (::nimi kohde)
    (when-let [osa (osa/fmt-kohteenosa osa)]
      (str (when (::nimi kohde) ", ") osa))))

(defn kohde-sisaltaa-sulun? [kohde]
  (boolean (some osa/sulku? (::kohteenosat kohde))))

(defn kohde-idlla [kohteet id]
  (first (filter #(= (::id %) id) kohteet)))

(s/def ::tallenna-kohde-kysely (s/keys :req [::nimi
                                             ::kohdekokonaisuus-id]
                                       :opt [::id
                                             ::kohteenosat]))

(s/def ::tallenna-kohde-vastaus ::kok/hae-kohdekokonaisuudet-ja-kohteet-vastaus)
