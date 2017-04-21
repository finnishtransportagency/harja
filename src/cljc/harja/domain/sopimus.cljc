(ns harja.domain.sopimus
  "Määrittelee sopimuksen specit"
  #?@(:clj [(:require [clojure.spec :as s]
                      [harja.id :refer [id-olemassa?]]
                      [harja.kyselyt.specql-db :refer [db]]
                      [specql.core :refer [define-tables]]
                      [clojure.future :refer :all])]
      :cljs [(:require [clojure.spec :as s]
               [harja.id :refer [id-olemassa?]]
               [specql.impl.registry]
               [specql.data-types])
             (:require-macros
               [harja.kyselyt.specql-db :refer [db]]
               [specql.core :refer [define-tables]])]))

(define-tables db ["sopimus" ::sopimus])

;; Haut

(s/def ::hae-harjassa-luodut-sopimukset-vastaus
  (s/coll-of (s/keys :req [::id ::nimi ::alkupvm ::loppupvm ::paasopimus])))

;; Tallennukset

(s/def ::tallenna-sopimus-kysely (s/keys
                                   :req [::nimi ::alkupvm ::loppupvm]
                                   :opt [::id ::paasopimus]))

(s/def ::tallenna-sopimus-vastaus (s/keys :req [::id ::nimi ::alkupvm ::loppupvm ::paasopimus]))


(defn paasopimus [sopimukset]
  (let [ps (as-> sopimukset s
                 (filter (comp id-olemassa? :id) s)
                 (remove :poistettu s)
                 (filter (comp some? #{(some :paasopimus s)} :id) s))]
    (assert (>= 1 (count ps)) (str (pr-str sopimukset) " löytyi useampi kuin yksi pääsopimus"))
    (first ps)))