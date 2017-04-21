(ns harja.domain.hanke
  "Määrittelee hankkeeseen liittyvät speksit"
  (:require [clojure.spec :as s]
            [harja.domain.urakka :as u]
            [harja.tyokalut.spec-apurit :as spec-apurit]
    #?@(:clj [
            [clojure.future :refer :all]])))

;; TODO Käytä define-tables
(s/def ::id ::spec-apurit/postgres-serial)
(s/def ::alkupvm inst?)
(s/def ::loppupvm inst?)
(s/def ::nimi string?)

;; Haut

(s/def ::hae-harjassa-luodut-hankkeet-vastaus
  (s/coll-of (s/keys :req [::id ::alkupvm ::loppupvm ::nimi]
                     :opt [::u/urakka])))

;; Tallennus

(s/def ::tallenna-hanke-kysely (s/keys :req [::alkupvm ::loppupvm ::nimi]
                                       :opt [::id]))

(s/def ::tallenna-hanke-vastaus (s/keys :req [::id ::alkupvm ::loppupvm ::nimi]))