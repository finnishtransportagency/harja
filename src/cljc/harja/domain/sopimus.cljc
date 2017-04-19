(ns harja.domain.sopimus
  "Määrittelee urakkaan liittyvien sopimuksien nimiavaruuden specit"
  (:require [clojure.spec :as s]
            [harja.domain.urakka :as u]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            #?@(:clj [[clojure.future :refer :all]])))

(s/def ::id ::spec-apurit/postgres-serial)
(s/def ::nimi ::spec-apurit/postgres-serial)
(s/def ::alkupvm inst?)
(s/def ::loppupvm inst?)
(s/def ::paasopimus (s/nilable ::spec-apurit/postgres-serial))

(s/def ::sopimus (s/keys
                   :req-un [::nimi ::alkupvm ::loppupvm ::paasopimus]
                   :opt-un [::id ::u/urakka]))

;; Haut

(s/def ::hae-harjassa-luodut-sopimukset-vastaus
  (s/coll-of ::sopimus))