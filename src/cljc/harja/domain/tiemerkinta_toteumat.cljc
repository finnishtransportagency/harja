(ns harja.domain.tiemerkinta-toteumat
  "Tienäkymän tietojen spec-määritykset"
  (:require [clojure.spec :as s]
    #?@(:clj [[clojure.future :refer :all]])))


(s/def ::hae-tiemerkinnan-yksikkohintaiset-tyot-kysely
  (s/keys :req-un [::urakka-id ::testilollero]))

(s/def ::tiemerkinnan-yksikkohintainen-tyo
  (s/keys :req-un [::selite ::muutospvm ::hintatyyppi ::yllapitoluokka ::id
                   ::pituus ::hinta-kohteelle
                   ::yllapitokohde-id ::tr-numero ::hinta]))

(s/def ::hae-tiemerkinnan-yksikkohintaiset-tyot-vastaus
  (s/coll-of ::tiemerkinnan-yksikkohintainen-tyo))



