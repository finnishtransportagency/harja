(ns harja.domain.organisaatio
  "Määrittelee organisaation nimiavaruuden specit"
  (:require [clojure.spec :as s]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            #?@(:clj [[clojure.future :refer :all]])))

(s/def ::id ::spec-apurit/postgres-serial)


;; Haut

(s/def ::vesivayla-urakoitsijat-vastaus
  (s/coll-of ::organisaatio))