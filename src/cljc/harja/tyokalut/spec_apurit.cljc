(ns harja.tyokalut.spec-apurit
  (:require [clojure.spec :as s]
    #?@(:clj [[clojure.future :refer :all]])))

;; PostgreSQL raja-arvot

(def postgres-int-min -2147483648)
(def postgres-int-max 2147483647)

(s/def ::postgres-int (s/and int? #(s/int-in-range? postgres-int-min postgres-int-max %)))
(s/def ::postgres-serial (s/and nat-int? #(s/int-in-range? 1 postgres-int-max %)))

;; Yleiset apufunktiot

(defn poista-nil-avaimet [mappi]
  (clojure.walk/postwalk
    (fn [elementti]
      (if (map? elementti)
        (let [m (into {} (remove (comp nil? second) elementti))]
          (when (seq m)
            m))
        elementti))
    mappi))