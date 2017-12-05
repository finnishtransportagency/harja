(ns harja.domain.sopimus
  "Määrittelee sopimuksen specit"
  #?@(:clj [(:require [clojure.spec.alpha :as s]
                      [harja.id :refer [id-olemassa?]]
                      [harja.kyselyt.specql-db :refer [define-tables]]
                      )]
      :cljs [(:require [clojure.spec.alpha :as s]
               [harja.id :refer [id-olemassa?]]
               [specql.impl.registry]
               [specql.data-types])
             (:require-macros
              [harja.kyselyt.specql-db :refer [define-tables]])]))

(define-tables
  ["sopimus" ::sopimus
   {"paasopimus" ::paasopimus-id
    "harjassa_luotu" ::harjassa-luotu?
    "urakoitsija_sampoid" ::urakoitsija-sampoid
    "urakka_sampoid" ::urakka-sampoid
    "luoja" ::luoja-id
    "urakka" ::urakka-id
    "muokkaaja" ::muokkaaja-id}])

(def perustiedot
  #{::id
    ::nimi
    ::paasopimus-id
    ::alkupvm
    ::loppupvm
    ::sampoid})

(defn- voi-olla-paasopimus?* [sopimus]
  (and
    (id-olemassa? (::id sopimus))
    (not (:poistettu sopimus))
    (nil? (::paasopimus-id sopimus))))

(defn paasopimus-jollekin? [sopimukset sopimus]
  (boolean
    (let [muut-sopimukset (filter #(not= (::id %) (::id sopimus))
                                 sopimukset)]
     (and
       (voi-olla-paasopimus?* sopimus)
       (some #(= (::paasopimus-id %) (::id sopimus))
             muut-sopimukset)))))

(defn paasopimus-jokaiselle? [sopimukset sopimus]
  (boolean
    (let [muut-sopimukset (filter #(not= (::id %) (::id sopimus))
                                 sopimukset)]
     (and
       (voi-olla-paasopimus?* sopimus)
       (every? #(= (::paasopimus-id %) (::id sopimus))
               muut-sopimukset)))))

(defn ainoa-paasopimus [sopimukset]
  (first (filter #(paasopimus-jokaiselle? sopimukset %) sopimukset)))

(defn sopimuksen-paasopimus [sopimukset sopimus]
  (first (filter #(= (::paasopimus-id sopimus) (::id %))
                 sopimukset)))

(s/def ::reimari-diaarinro (s/nilable string?))
;; Haut

(s/def ::hae-harjassa-luodut-sopimukset-vastaus
  (s/coll-of (s/keys :req [::id ::nimi ::reimari-diaarinro ::alkupvm ::loppupvm ::paasopimus-id])))

;; Tallennukset

(s/def ::tallenna-sopimus-kysely (s/keys
                                   :req [::nimi ::alkupvm ::loppupvm]
                                   :opt [::id ::reimari-diaarinro ::paasopimus-id]))

(s/def ::tallenna-sopimus-vastaus (s/keys :req [::id ::nimi ::reimari-diaarinro ::alkupvm ::loppupvm ::paasopimus-id]))
