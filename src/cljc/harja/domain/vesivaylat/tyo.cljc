(ns harja.domain.vesivaylat.tyo
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.domain.muokkaustiedot :as m]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]
    [specql.rel :as rel]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["vv_tyo" ::tyo
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot
   {::hinnoittelu (specql.rel/has-one
                    ::hinnoittelu-id
                    :harja.domain.vesivaylat.hinnoittelu/hinnoittelu
                    :harja.domain.vesivaylat.hinnoittelu/id)
    ::toimenpidekoodi (specql.rel/has-one
                        ::toimenpidekoodi-id
                        :harja.domain.toimenpidekoodi/toimenpidekoodi
                        :harja.domain.toimenpidekoodi/id)}])

;; Löysennetään tyyppejä numeroiksi, koska JS-maailmassa ei ole BigDeccejä
(s/def ::maara number?)

(def perustiedot
  #{::id
    ::maara})

(def viittaus-idt
  #{::hinnoittelu-id
    ::toimenpidekoodi-id})

(defn- paivita-hintajoukon-hinta-ominaisuudella
  [tyot ominaisuus uudet-hintatiedot]
  (mapv (fn [tyo]
          (if (= (ominaisuus tyo) (ominaisuus uudet-hintatiedot))
            (assoc tyo ::maara (::maara uudet-hintatiedot))
            tyo))
        tyot))

(defn- paivita-tyojoukon-tyon-maara-idlla
  "Päivittää hintojen joukosta yksittäisen hinnan, jolla annettu otsikko."
  [tyot uudet-hintatiedot]
  (paivita-hintajoukon-hinta-ominaisuudella tyot ::id uudet-hintatiedot))