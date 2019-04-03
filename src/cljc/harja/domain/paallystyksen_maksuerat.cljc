(ns harja.domain.paallystyksen-maksuerat
  "Päällystyksen maksuerien spec-määritykset"
  (:require
    [clojure.spec.alpha :as s]
    [harja.pvm :as pvm]
    [harja.domain.urakka :as urakka]
    [harja.domain.sopimus :as sopimus]
    [harja.domain.tierekisteri :as tierekisteri]
    [harja.domain.yllapitokohde :as yllapitokohde]
    [harja.tyokalut.spec-apurit :as spec-apurit]))

;; Maksuerä

(s/def ::id ::spec-apurit/postgres-serial)
(s/def ::sisalto (s/nilable string?))
(s/def ::maksueranumero (s/and nat-int? #(s/int-in-range? 1 6 %)))
(s/def ::maksueratunnus (s/nilable string?))

(s/def ::maksuera (s/keys :req-un [::maksueranumero ::sisalto]
                          :opt-un [::id]))
(s/def ::maksuerat (s/coll-of ::maksuera))

(s/def ::tr-numero ::tierekisteri/numero)

(s/def ::yllapitokohde-maksuerineen
  (s/keys :req-un [::yllapitokohde/id
                   ::yllapitokohde/nimi
                   ::yllapitokohde/kokonaishinta
                   ::maksuerat
                   ::tr-numero]
          :opt-un [::yllapitokohde/kohdenumero ::maksueratunnus]))

;; Haut

(s/def ::hae-paallystyksen-maksuerat-kysely
  (s/keys :req [::urakka/id ::sopimus/id ::urakka/vuosi]))

(s/def ::hae-paallystyksen-maksuerat-vastaus
  (s/coll-of ::yllapitokohde-maksuerineen))

;; Tallennukset

(s/def ::tallennettava-yllapitokohde-maksuerineen
  (s/keys :req-un [::yllapitokohde/id
                   ::maksuerat]
          :opt-un [::maksueratunnus]))

(s/def ::yllapitokohteet (s/coll-of ::tallennettava-yllapitokohde-maksuerineen))

(s/def ::toteumat (s/coll-of ::tiemerkinnan-yksikkohintainen-tyo))
(s/def ::tallenna-paallystyksen-maksuerat-kysely
  (s/keys :req [::urakka/id ::sopimus/id ::urakka/vuosi]
          :req-un [::yllapitokohteet]))

(s/def ::tallenna-paallystyksen-maksuerat-vastaus
  ::hae-paallystyksen-maksuerat-vastaus)
