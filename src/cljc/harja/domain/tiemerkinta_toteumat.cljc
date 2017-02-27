(ns harja.domain.tiemerkinta-toteumat
  "Tienäkymän tietojen spec-määritykset"
  (:require
    [clojure.spec :as s]
    [harja.pvm :as pvm]
    [harja.domain.yllapitokohteet :as yllapitokohteet]
    [harja.domain.tierekisteri :as tr-domain]
    [harja.tyokalut.spec-apurit :as apurit]
    #?@(:clj [[clojure.future :refer :all]])))

;; Toteuma

(s/def ::selite string?)
(s/def ::muutospvm #?(:clj  (s/nilable inst?)
                      :cljs (s/nilable inst?)))
(s/def ::hintatyyppi #{:toteuma :suunnitelma})
(s/def ::yllapitoluokka (s/nilable ::yllapitokohteet/yllapitoluokka))
(s/def ::id (s/nilable int?))
(s/def ::pituus (s/nilable ::tr-domain/pituus))
(s/def ::hinta-kohteelle (s/nilable (s/and string? #(>= (count %) 1))))
(s/def ::yllapitokohde-id (s/nilable ::apurit/postgres-serial))
(s/def ::tr-numero (s/nilable ::tr-domain/numero))
(s/def ::hinta (s/double-in :min 0 :max 10000000 :infinite? false :NaN? false))
(s/def ::poistettu boolean?)

(s/def ::tiemerkinnan-yksikkohintainen-tyo
  (s/keys :req-un [::selite ::muutospvm ::hintatyyppi ::yllapitoluokka ::id
                   ::pituus ::yllapitokohde-id ::hinta-kohteelle
                   ::tr-numero ::hinta]
          :opt-un [::poistettu]))

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