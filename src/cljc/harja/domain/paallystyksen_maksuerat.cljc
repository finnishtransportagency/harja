(ns harja.domain.paallystyksen-maksuerat
  "Päällystyksen maksuerien spec-määritykset"
  (:require
    [clojure.spec :as s]
    [harja.pvm :as pvm]
    [harja.domain.urakka :as urakka]
    [harja.domain.sopimus :as sopimus]
    #?@(:clj [
    [clojure.future :refer :all]])
    [harja.tyokalut.spec-apurit :as spec-apurit]))

;; Toteuma

(s/def ::id ::spec-apurit/postgres-serial)
(s/def ::sisalto string?)
(s/def ::maksueranumero (s/and nat-int? #(s/int-in-range? 1 5 %)))

(s/def ::paallystyksen-maksuera (s/keys :req [::id ::sisalto ::maksueranumero]))

(s/def ::maksuerat (s/coll-of ::paallystyksen-maksuera))

(s/def ::paallystyksen-maksuera
  (s/keys :req [::maksuerat]
          :req-un [::yllapitokohde-id ::kohdenumero ::nimi ::tr-numero]))

;; Haut

(s/def ::hae-paallystyksen-maksuerat-kysely
  (s/keys :req [::urakka/id ::sopimus/id ::urakka/vuosi]))

(s/def ::hae-paallystyksen-maksuerat-vastaus
  (s/coll-of ::paallystyksen-maksuera))

;; Tallennukset

;(s/def ::toteumat (s/coll-of ::tiemerkinnan-yksikkohintainen-tyo))
;(s/def ::tallenna-paallystyksen-maksuerat-kysely
;  (s/keys :req-un [::urakka-id ::toteumat]))

(s/def ::tallenna-paallystyksen-maksuerat-vastaus
  ::hae-paallystyksen-maksuerat-vastaus)