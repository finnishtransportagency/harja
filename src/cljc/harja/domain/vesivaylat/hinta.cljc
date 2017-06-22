(ns harja.domain.vesivaylat.hinta
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
  ["vv_hinta" ::hinta
   {"muokattu" ::m/muokattu
    "muokkaaja" ::m/muokkaaja-id
    "luotu" ::m/luotu
    "luoja" ::m/luoja-id
    "poistettu" ::m/poistettu?
    "poistaja" ::m/poistaja-id
    #?@(:clj [::hinnoittelu (rel/has-one
                              ::hinnoittelu-id
                              :harja.domain.vesivaylat.hinnoittelu/hinnoittelu
                              :harja.domain.vesivaylat.hinnoittelu/id)])}])

;; Löysennetään tyyppejä numeroiksi, koska JS-maailmassa ei ole BigDeccejä
(s/def ::maara number?)
(s/def ::yleiskustannuslisa number?)

(def perustiedot
  #{::otsikko
    ::maara
    ::yleiskustannuslisa
    ::id})

(def viittaus-idt
  #{::hinnoittelu-id})

(def metatiedot m/muokkauskentat)

;; Yleinen yleiskustannuslisä (%), joka käytössä sopimuksissa
(def yleinen-yleiskustannuslisa 12)

(defn yleiskustannuslisien-osuus
  "Palauttaa hintojen yleiskustannusten osuuden"
  [hinnat]
  (reduce + 0
          (keep
            (fn [hinta]
              (let [maara (::maara hinta)
                    yleiskustannuslisa (::yleiskustannuslisa hinta)]
                (when yleiskustannuslisa
                  (- (* (+ (/ yleiskustannuslisa 100) 1) maara) maara))))
            hinnat)))

(defn perushinta
  "Palauttaa hintojen summan ilman yleiskustannuslisiä"
  [hinnat]
  (reduce + 0 (map ::maara hinnat)))

(defn hinnan-ominaisuus [hinnat otsikko ominaisuus]
  (->> hinnat
       (filter #(= (::otsikko %) otsikko))
       (first)
       ominaisuus))

(defn hinnan-maara [hinnat otsikko]
  (hinnan-ominaisuus hinnat otsikko ::maara))

(defn hinnan-yleiskustannuslisa [hinnat otsikko]
  (hinnan-ominaisuus hinnat otsikko ::yleiskustannuslisa))

(defn kokonaishinta-yleiskustannuslisineen [hinnat]
  (+ (perushinta hinnat)
     (yleiskustannuslisien-osuus hinnat)))

(defn hinta-otsikolla [otsikko hinnat]
  (first (filter #(= (::otsikko %) otsikko) hinnat)))