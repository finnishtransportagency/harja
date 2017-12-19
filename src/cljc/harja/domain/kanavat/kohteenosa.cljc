(ns harja.domain.kanavat.kohteenosa
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

    [harja.domain.muokkaustiedot :as m])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kohteenosa_tyyppi" ::kohteenosa-tyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["liikennetapahtuma_palvelumuoto" ::osan-palvelumuoto (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_kohteenosa" ::kohteenosa
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::kohde (specql.rel/has-one ::kohde-id
                                :harja.domain.kanavat.kohde/kohde
                                :harja.domain.kanavat.kohde/id)}])

(def perustiedot
  #{::id
    ::tyyppi
    ::nimi
    ::oletuspalvelumuoto
    ::kohde-id})

(def fmt-kohdeosa-tyyppi
  {:sulku "sulku"
   :silta "silta"
   :rautatiesilta "rautatiesilta"})


(defn fmt-kohdeosa
  "Palauttaa kohdeosan nimen tai tyypin formatoituna."
  [kohdeosa]
  (when-let [s (or (::nimi kohdeosa)
                   (fmt-kohdeosa-tyyppi (::tyyppi kohdeosa)))]
    (str/capitalize s)))

(defn silta? [osa]
  (or (= :silta (::tyyppi osa))
      (= :rautatiesilta (::tyyppi osa))))

(defn sulku? [osa]
  (= :sulku (::tyyppi osa)))
