(ns harja.kyselyt.bonus-konfiguraatio
  (:require [harja.kyselyt.konversio :as konv]
            [jeesql.core :refer [defqueries]]))

(declare hae-bonus-profiilit-admin hae-urakan-bonus-profiilit hae-bonus-profiili-admin
  hae-bonus-profiilin-rivit hae-bonus-profiilin-rivit-admin)

(defn- muunna-urakkatyyppi
  [rivi avainpolku]
  (if (get-in rivi avainpolku)
    (update-in rivi avainpolku keyword)
    rivi))

(defn- normalisoi-vektoriksi
  [arvo]
  (cond
    (nil? arvo) []
    (vector? arvo) arvo
    (instance? java.sql.Array arvo) (vec (.getArray ^java.sql.Array arvo))
    :else [arvo]))

(defn muunna-bonus-profiili-admin-listarivi
  [{:as rivi}]
  (-> rivi
    konv/alaviiva->rakenne
    (muunna-urakkatyyppi [:urakkatyyppi])))

(defn muunna-bonus-konfiguraatiorivi
  [{:as rivi}]
  (let [rivi (konv/alaviiva->rakenne rivi)
        urakkarajausten-maara (or (get-in rivi [:profiilirivi :urakkarajausten-maara])
                                (get-in rivi [:profiilirivi :urakkarajausten :maara]))]
    (cond-> rivi
      (some? urakkarajausten-maara)
      (->
        (assoc-in [:profiilirivi :urakkarajausten-maara] urakkarajausten-maara)
        (update :profiilirivi dissoc :urakkarajausten))

      (get-in rivi [:profiilirivi :urakat])
      (update-in [:profiilirivi :urakat] normalisoi-vektoriksi)

      (get-in rivi [:profiilirivi :toimenpideinstanssi :t2 :koodi])
      (->
        (assoc-in [:profiilirivi :toimenpideinstanssi-t2-koodi]
          (get-in rivi [:profiilirivi :toimenpideinstanssi :t2 :koodi]))
        (update-in [:profiilirivi :toimenpideinstanssi] dissoc :t2))

      (get-in rivi [:profiilirivi :toimenpideinstanssi :rajauksen :tyyppi])
      (->
        (assoc-in [:profiilirivi :toimenpideinstanssi-rajauksen-tyyppi]
          (keyword (get-in rivi [:profiilirivi :toimenpideinstanssi :rajauksen :tyyppi])))
        (update-in [:profiilirivi :toimenpideinstanssi] dissoc :rajauksen))

      (get-in rivi [:profiili :urakkatyyppi])
      (muunna-urakkatyyppi [:profiili :urakkatyyppi])

      (get-in rivi [:laji :koodi])
      (update-in [:laji :koodi] keyword))))

(defqueries "harja/kyselyt/bonus_konfiguraatio.sql")
