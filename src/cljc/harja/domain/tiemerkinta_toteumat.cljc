(ns harja.domain.tiemerkinta-toteumat
  "Tienäkymän tietojen spec-määritykset"
  (:require [clojure.spec :as s]
    #?@(:clj [[clojure.future :refer :all]])
            [harja.geo :as geo]))

(s/def ::tallenna-tiemerkinnan-yksikkohintaiset-tyot
  (s/keys :req-un [::urakka-id]))

#_(s/def ::hae-tiemerkinnan-yksikkohintaiset-tyot
  (s/keys :req-un [::urakka-id]))

