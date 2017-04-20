(ns harja.domain.sopimus
  "Määrittelee urakkaan liittyvien sopimuksien nimiavaruuden specit"
  (:require [clojure.spec :as s]
            [harja.domain.urakka :as u]
            [harja.id :refer [id-olemassa?]]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            #?@(:clj [[clojure.future :refer :all]])))

(s/def ::id ::spec-apurit/postgres-serial)
(s/def ::nimi string?)
(s/def ::alkupvm inst?)
(s/def ::loppupvm inst?)
(s/def ::paasopimus (s/nilable ::spec-apurit/postgres-serial))

(s/def ::sopimus (s/keys
                   :req [::nimi ::alkupvm ::loppupvm]
                   :opt [::id ::paasopimus]))

;; Haut

(s/def ::hae-harjassa-luodut-sopimukset-vastaus
  (s/coll-of ::sopimus))

;; Tallennukset

(s/def ::tallenna-sopimus-kysely ::sopimus)
(s/def ::tallenna-sopimus-vastaus ::sopimus)


(defn paasopimus [sopimukset]
  (let [ps (as-> sopimukset s
                 (filter (comp id-olemassa? :id) s)
                 (remove :poistettu s)
                 (filter (comp some? #{(some :paasopimus s)} :id) s))]
    (assert (>= 1 (count ps)) (str (pr-str sopimukset) " löytyi useampi kuin yksi pääsopimus"))
    (first ps)))