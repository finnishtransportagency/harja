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

    [harja.domain.muokkaustiedot :as m])
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

(def perustiedot+kanava
  #{[:harja.domain.kanava.hairiotilanne/kohde perustiedot
     [::kohteen-kanava #{:harja.domain.kanavat.kanava/id
                         :harja.domain.kanavat.kanava/nimi}]]})

(defn tyyppi->str [kohde]
  ({:silta "silta"
    :sulku "sulku"
    :sulku-ja-silta "sulku ja silta"}
    kohde))

(def metatiedot m/muokkauskentat)

(def perustiedot-ja-sijainti (conj perustiedot ::sijainti))

(def kohteen-urakkatiedot #{[::linkin-urakka #{:harja.domain.urakka/id :harja.domain.urakka/nimi}]})

;; Domain-funktiot

(defn fmt-kohde-ja-kanava
  "Ottaa mapin, jossa on kohteen tiedot ja ::kohteen-kanava avaimen takana kanavan tiedot."
  [kohde-ja-kanava]
  (str
    (when-let [kanava-nimi (get-in kohde-ja-kanava [::kohteen-kanava :harja.domain.kanavat.kanava/nimi])]
      (str kanava-nimi ", "))
    (when-let [kohde-nimi (::nimi kohde-ja-kanava)]
      (str kohde-nimi ", "))
    (when-let [kohde-tyyppi (tyyppi->str (::tyyppi kohde-ja-kanava))]
      (str kohde-tyyppi))))