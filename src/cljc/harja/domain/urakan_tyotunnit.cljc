(ns harja.domain.urakan-tyotunnit
  "Urakan työtuntien skeemat."
  (:require [clojure.spec.alpha :as s]
            [specql.impl.registry]
            [specql.data-types]
            [harja.pvm :as pvm]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            
            [clj-time.core :as t]])
    #?(:cljs [cljs-time.core :as t]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["urakan_tyotunnit" ::urakan-tyotunnit {"lahetys_onnistunut" ::lahetys-onnistunut
                                          "urakka" ::urakka-id}])

(def kaikki-kentat
  #{::id
    ::urakka-id
    ::vuosi
    ::vuosikolmannes
    ::tyotunnit
    ::lahetetty
    ::lahetys-onnistunut})

(s/def ::urakan-tyotunnit-vuosikolmanneksittain (s/coll-of ::urakan-tyotunnit))

(s/def ::urakan-tyotuntien-haku (s/keys :req [::urakka-id]
                                        :opt [::vuosi ::vuosikolmannes]))

(s/def ::urakan-tyotuntien-tallennus (s/keys :req [::urakka-id ::urakan-tyotunnit-vuosikolmanneksittain]))

(s/def ::urakan-kuluvan-vuosikolmanneksen-tyotuntien-haku (s/keys :req [::urakka-id]))

(s/def ::urakan-kuluvan-vuosikolmanneksen-tyotuntien-hakuvastaus (s/keys :opt [::urakan-tyotunnit]))

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

(defn kuluvan-vuosikolmanneksen-paattymispaiva[]
  (let [{vuosi ::vuosi kolmannes ::vuosikolmannes} (kuluva-vuosikolmannes)
        kuukausi (cond (= kolmannes 1) 4
                       (= kolmannes 2) 8
                       (= kolmannes 3) 12
                       :else 1)]
    (t/last-day-of-the-month vuosi kuukausi)))
