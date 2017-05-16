(ns harja.domain.sopimus
  "Määrittelee sopimuksen specit"
  #?@(:clj [(:require [clojure.spec.alpha :as s]
                      [harja.id :refer [id-olemassa?]]
                      [harja.kyselyt.specql-db :refer [define-tables]]
                      [clojure.future :refer :all])]
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

(defn paasopimus? [sopimukset sopimus]
  (let [muut-sopimukset (filter #(not= (::id %) (::id sopimus))
                                sopimukset)]
    (and
      (id-olemassa? (::id sopimus))
      (not (:poistettu sopimus))
      (nil? (::paasopimus-id sopimus))
      (every? #(= (::paasopimus-id %) (::id sopimus))
              muut-sopimukset))))

(defn paasopimus [sopimukset]
  (first (filter #(paasopimus? sopimukset %) sopimukset)))


;; Haut

(s/def ::hae-harjassa-luodut-sopimukset-vastaus
  (s/coll-of (s/keys :req [::id ::nimi ::alkupvm ::loppupvm ::paasopimus-id])))

;; Tallennukset

(s/def ::tallenna-sopimus-kysely (s/keys
                                   :req [::nimi ::alkupvm ::loppupvm]
                                   :opt [::id ::paasopimus-id]))

(s/def ::tallenna-sopimus-vastaus (s/keys :req [::id ::nimi ::alkupvm ::loppupvm ::paasopimus-id]))
