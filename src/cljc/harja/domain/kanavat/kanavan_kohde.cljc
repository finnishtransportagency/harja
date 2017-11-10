(ns harja.domain.kanavat.kanavan-kohde
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
    [harja.domain.kanavat.kanava :as kanava])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kohteen_tyyppi" ::kohteen-tyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_kohde" ::kohde
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::kohteen-kanava (specql.rel/has-one ::kanava-id
                                         :harja.domain.kanavat.kanava/kanava
                                         :harja.domain.kanavat.kanava/id)}]
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
    ::tyyppi})
(defn tyyppi->str [kohde]
  ({:silta "silta"
    :sulku "sulku"
    :sulku-ja-silta "sulku ja silta"}
    kohde))

(def metatiedot m/muokkauskentat)

(def perustiedot-ja-sijainti (conj perustiedot ::sijainti))

(def kohteen-urakkatiedot #{[::linkin-urakka #{:harja.domain.urakka/id :harja.domain.urakka/nimi}]})

(def kohteen-kanavatiedot #{[::kohteen-kanava kanava/perustiedot]})

(def perustiedot-ja-kanava
  (set/union perustiedot kohteen-kanavatiedot))
;; Domain-funktiot

(defn- kohteen-nimi* [kanava-nimi kohteen-nimi kohteen-tyyppi]
  (str
    (when kanava-nimi
      (str kanava-nimi ", "))
    (when kohteen-nimi
      (str kohteen-nimi ", "))
    (when kohteen-tyyppi
      (str kohteen-tyyppi))))

;; Kohteen nimen formatointi on hieman monimutkaista, koska täyteen nimeen kuuluu
;; kanavan nimi, kohteen nimi (joka usein tyhjä?), ja kohteen tyyppi.
;; Koska nämä tiedot nousevat kannasta hieman eri muodossa, riippuen kyselyistä,
;; formatointifunktioitakin on kehittynyt useammanlaisia.
;; Tästä ei varsinaisesti ole muuta haittaa kuin nimeämisen vaikeus

(defn fmt-kohteen-kanava-nimi
  "Ottaa mapin, jossa on kohteen tiedot ja ::kohteen-kanava avaimen takana kanavan tiedot."
  [kohde-ja-kanava]
  (kohteen-nimi* (get-in kohde-ja-kanava [::kohteen-kanava ::kanava/nimi])
                 (::nimi kohde-ja-kanava)
                 (tyyppi->str (::tyyppi kohde-ja-kanava))))

(defn fmt-kanava-ja-kohde-nimi
  "Ottaa kanavan mäpissä, jossa on ::kanava/nimi, ja kohteen mäpissä, jossa on ::kohde/nimi ja ::kohde/tyyppi.
  Palauttaa kohteen täyden nimen."
  [kanava kohde]
  (kohteen-nimi* (::kanava/nimi kanava)
                 (::nimi kohde)
                 (tyyppi->str (::tyyppi kohde))))