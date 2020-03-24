(ns harja.tyokalut.spec-apurit
  (:require [clojure.spec.alpha :as s]))

;; PostgreSQL raja-arvot

(def postgres-int-min -2147483648)
(def postgres-int-max 2147483647)

(s/def ::postgres-int (s/and int? #(s/int-in-range? postgres-int-min postgres-int-max %)))
(s/def ::postgres-serial (s/and nat-int? #(s/int-in-range? 1 postgres-int-max %)))

;; Yleiset speckit
(s/def ::positive-int? (s/and integer? #(>= % 0)))
(s/def ::positive-number? (s/and number? #(>= % 0) #(not= % ##Inf) #?(:cljs #(not (.isNaN js/Number %)))))

;; Yleiset apufunktiot

(defn poista-nil-avaimet
  ([mappi] (poista-nil-avaimet mappi true))
  ([mappi poista-tyhjat-mapit?]
   (clojure.walk/postwalk
     (fn [elementti]
       (if (and (map? elementti) (not (record? elementti)))
         (let [m (into {} (remove (comp nil? second) elementti))]
           (when (or (seq m)
                     (not poista-tyhjat-mapit?))
             m))
         elementti))
     mappi)))

(defn poista-ei-namespacetetut-avaimet [mappi]
  (let [poistettavat-avaimet (filter (comp not namespace) (keys mappi))]
    (apply dissoc mappi poistettavat-avaimet)))
