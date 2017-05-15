(ns harja.domain.urakan-tyotunnit
  "Urakan työtuntien skeemat."
  (:require [specql.impl.registry]
            [specql.data-types]
            [clojure.spec :as s]
            [harja.pvm :as pvm]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            [clojure.future :refer :all]
            [clj-time.core :as t]])
    #?(:cljs [cljs-time.core :as t]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["urakan_tyotunnit" ::urakan-tyotunnit {"lahetys_onnistunut" ::lahetys-onnistunut
                                                              "urakka" ::urakka-id}])

(s/def ::urakan-tyotunnit-vuosikolmanneksittain (s/coll-of ::urakan-tyotunnit))

(s/def ::urakan-tyotunntien-haku (s/keys :req [::urakka-id]
                                         :opt [::vuosi ::vuosikolmannes]))

(s/def ::urakan-kuluvan-vuosikolmanneksen-tyotuntien-haku (s/keys :req [::urakka-id]))

(def vuosikolmannesten-kuukaudet
  [[1 4] [5 8] [9 12]])

(defn urakan-vuosikolmannekset [alkupvm loppupvm]
  (let [kolmannes (fn [v [alku-kk loppu-kk]]
                    {:alku (t/first-day-of-the-month v alku-kk)
                     :loppu (t/last-day-of-the-month v loppu-kk)})]
    (into {}
          (map (juxt identity (fn [vuosi]
                                {1 (kolmannes vuosi (nth vuosikolmannesten-kuukaudet 0))
                                 2 (kolmannes vuosi (nth vuosikolmannesten-kuukaudet 1))
                                 3 (kolmannes vuosi (nth vuosikolmannesten-kuukaudet 2))})))
          (range (pvm/vuosi alkupvm) (inc (pvm/vuosi loppupvm))))))

(defn kuluva-vuosikolmannes []
  (let [valissa? (fn [[alku-kk loppu-kk] kuukausi]
                   (and
                     (>= kuukausi alku-kk)
                     (<= kuukausi loppu-kk)))
        nyt (t/now)
        kuukausi (t/month nyt)
        vuosi (t/year nyt)
        kolmannes (cond
                    (valissa? (nth vuosikolmannesten-kuukaudet 0) kuukausi) 1
                    (valissa? (nth vuosikolmannesten-kuukaudet 1) kuukausi) 2
                    (valissa? (nth vuosikolmannesten-kuukaudet 2) kuukausi) 3
                    :else (assert false (str "Tuntematon kuukausi:" kuukausi)))]
    {::vuosi vuosi
     ::vuosikolmannes kolmannes}))