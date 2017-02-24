(ns harja.domain.tiemerkinta-toteumat
  "Tienäkymän tietojen spec-määritykset"
  (:require
    [clojure.spec :as s]
    [harja.pvm :as pvm]
    #?@(:clj [
    [clojure.future :refer :all]])))

(s/def ::selite string?)
(s/def ::muutospvm #?(:clj inst?
                      :cljs inst?))
(s/def ::hintatyyppi #{:toteuma :suunnitelma})
(s/def ::yllapitoluokka (s/and int? pos?))
(s/def ::id int?)
(s/def ::pituus (s/and number? pos?))
(s/def ::hinta-kohteelle (s/and number? pos?))
(s/def ::yllapitokohde-id (s/or :puuttuu nil?
                                :annettu (s/and number? pos?)))
(s/def ::tr-numero (s/and int? pos?))
(s/def ::hinta (s/and number? pos?))


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



