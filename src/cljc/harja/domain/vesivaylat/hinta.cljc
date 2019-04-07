(ns harja.domain.vesivaylat.hinta
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.domain.muokkaustiedot :as m]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    
    [specql.rel :as rel]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["vv_hinta" ::hinta
   harja.domain.muokkaustiedot/muokkaus-ja-poistotiedot
   {::ryhma (specql.transform/transform (specql.transform/to-keyword))
    ::hinnoittelu (specql.rel/has-one
                    ::hinnoittelu-id
                    :harja.domain.vesivaylat.hinnoittelu/hinnoittelu
                    :harja.domain.vesivaylat.hinnoittelu/id)}])

;; Löysennetään tyyppejä numeroiksi, koska JS-maailmassa ei ole BigDeccejä
(s/def ::summa (s/nilable number?))
(s/def ::maara (s/nilable number?))
(s/def ::yksikkohinta (s/nilable number?))
(s/def ::yleiskustannuslisa number?)

(def perustiedot
  #{::otsikko
    ::summa
    ::yleiskustannuslisa
    ::komponentti-id
    ::komponentti-tilamuutos
    ::maara
    ::yksikkohinta
    ::yksikko
    ::ryhma
    ::id})

(def viittaus-idt
  #{::hinnoittelu-id})

(def metatiedot m/muokkauskentat)

;; Yleinen yleiskustannuslisä (%), joka käytössä sopimuksissa
(def yleinen-yleiskustannuslisa 12)

(defn hinnan-yklisan-osuus [hinta]
  (let [maara (or (::summa hinta) (* (::yksikkohinta hinta) (::maara hinta)))
        yleiskustannuslisa (::yleiskustannuslisa hinta)]
    (when yleiskustannuslisa
      (- (* (+ (/ yleiskustannuslisa 100) 1) maara) maara))))

(defn yklisien-osuus
  "Palauttaa hintojen yleiskustannusten osuuden"
  [hinnat]
  (reduce + 0
          (keep
            hinnan-yklisan-osuus
            hinnat)))

(defn hinnan-summa-ilman-yklisaa [hinta]
  (if (and (::yksikkohinta hinta) (::maara hinta))
    (* (::yksikkohinta hinta) (::maara hinta))
    (::summa hinta)))

(defn hintojen-summa-ilman-yklisaa
  "Palauttaa hintojen summan ilman yleiskustannuslisiä"
  [hinnat]
  (reduce + 0
          (map
            hinnan-summa-ilman-yklisaa
            hinnat)))

(defn hinnan-ominaisuus-otsikolla [hinnat otsikko ominaisuus]
  (->> hinnat
       (filter #(= (::otsikko %) otsikko))
       (first)
       ominaisuus))

(defn hinnan-summa-otsikolla [hinnat otsikko]
  (hinnan-ominaisuus-otsikolla hinnat otsikko ::summa))

(defn hinnan-kokonaishinta-yleiskustannuslisineen [hinta]
  (+ (hinnan-summa-ilman-yklisaa hinta)
     (hinnan-yklisan-osuus hinta)))

(defn kokonaishinta-yleiskustannuslisineen [hinnat]
  (+ (hintojen-summa-ilman-yklisaa hinnat)
     (yklisien-osuus hinnat)))

(defn hinta-otsikolla [hinnat otsikko]
  (first (filter #(= (::otsikko %) otsikko) hinnat)))

(defn hinta-idlla [hinnat id]
  (first (filter #(= (::id %) id) hinnat)))

(defn- paivita-hintajoukon-hinnan-tiedot-otsikolla
  "Päivittää hintojen joukosta yksittäisen hinnan, jolla annettu otsikko."
  [hinnat tiedot]
  (mapv (fn [hinta]
          (if (= (::otsikko hinta) (::otsikko tiedot))
            (merge hinta tiedot)
            hinta))
        hinnat))

(defn- paivita-hintajoukon-hinnan-tiedot-idlla
  "Päivittää hintojen joukosta yksittäisen hinnan, jolla annettu id."
  [hinnat tiedot]
  (mapv (fn [hinta]
          (if (= (::id hinta) (::id tiedot))
            (merge hinta tiedot)
            hinta))
        hinnat))
