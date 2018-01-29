(ns harja.domain.kanavat.kohteenosa
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.kyselyt.specql :as harja-specql]
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
                                :harja.domain.kanavat.kohde/id)}
   #?(:clj {::sijainti (specql.transform/transform (harja.kyselyt.specql/->Geometry))})])

(def perustiedot
  #{::id
    ::tyyppi
    ::nimi
    ::oletuspalvelumuoto
    ::sijainti})

(def kohteen-tiedot
  #{[::kohde #{:harja.domain.kanavat.kohde/nimi
               :harja.domain.kanavat.kohde/id}]})

(def fmt-kohteenosa-tyyppi
  {:sulku "sulku"
   :silta "silta"
   :rautatiesilta "rautatiesilta"})


(defn fmt-kohteenosa
  "Palauttaa kohteenosan 'nimen', joka on nimettömälle osalle tyyppi, nimetylle osalle nimi, tyyppi."
  [osa]
  (let [nimi (::nimi osa)
        tyyppi (fmt-kohteenosa-tyyppi (::tyyppi osa))]
    (when (or nimi tyyppi)
      (str/capitalize
        (str nimi (when (and nimi tyyppi) ", ") tyyppi)))))

(defn silta? [osa]
  (or (= :silta (::tyyppi osa))
      (= :rautatiesilta (::tyyppi osa))))

(defn sulku? [osa]
  (= :sulku (::tyyppi osa)))

(s/def ::hae-kohteenosat-vastaus (s/coll-of (s/keys :req [::id
                                                          ::tyyppi
                                                          ::sijainti]
                                                    :opt [::kohde
                                                          ::nimi
                                                          ::oletuspalvelumuoto])))
