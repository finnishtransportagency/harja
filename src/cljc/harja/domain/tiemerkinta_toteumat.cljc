(ns harja.domain.tiemerkinta-toteumat
  "Tienäkymän tietojen spec-määritykset"
  (:require
    [clojure.spec :as s]
    [harja.pvm :as pvm]
    [harja.domain.yllapitokohteet :as yllapitokohteet]
    [harja.domain.tierekisteri :as tr-domain]
    #?@(:clj [[clojure.future :refer :all]])))

(s/def ::selite string?)
(s/def ::muutospvm #?(:clj inst?
                      :cljs inst?))
(s/def ::hintatyyppi #{:toteuma :suunnitelma})
(s/def ::yllapitoluokka ::yllapitokohteet/yllapitoluokka)
(s/def ::id int?)
(s/def ::pituus ::tr-domain/pituus)
(s/def ::hinta-kohteelle string?)
(s/def ::yllapitokohde-id (s/or :puuttuu nil?
                                :annettu (s/and int? pos?)))
(s/def ::tr-numero ::tr-domain/numero)
(s/def ::hinta (s/and number? pos? #(< % 1000000000)))


(s/def ::tiemerkinnan-yksikkohintainen-tyo
  (s/keys :req-un [::selite ::muutospvm ::hintatyyppi ::yllapitoluokka ::id
                   ::pituus ::hinta-kohteelle
                   ::yllapitokohde-id ::tr-numero ::hinta]))

;; Haut

(s/def ::hae-tiemerkinnan-yksikkohintaiset-tyot-kysely
  (s/keys :req-un [::urakka-id]))

(s/def ::hae-tiemerkinnan-yksikkohintaiset-tyot-vastaus
  (s/coll-of ::tiemerkinnan-yksikkohintainen-tyo))

;; Tallennukset

(s/def ::tallenna-tiemerkinnan-yksikkohintaiset-tyot-kysely
  (s/keys :req-un [::urakka-id int?
                   ::toteumat (s/coll-of ::tiemerkinnan-yksikkohintainen-tyo)]))

(s/def ::tallenna-tiemerkinnan-yksikkohintaiset-tyot-vastaus
  ::hae-tiemerkinnan-yksikkohintaiset-tyot-vastaus)



