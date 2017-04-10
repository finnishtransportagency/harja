(ns harja.kyselyt.yllapitokohteet
  (:require [jeesql.core :refer [defqueries]]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/yllapitokohteet.sql")

(def kohdeosa-xf (geo/muunna-pg-tulokset :sijainti))

(defn liita-kohdeosat [db kohde yllapitokohde-id]
  (assoc kohde
    :kohdeosat
    (into []
          kohdeosa-xf
          (hae-urakan-yllapitokohteen-yllapitokohdeosat
            db {:yllapitokohde yllapitokohde-id}))))

(defn liita-kohdeosat-kohteisiin [db yllapitokohteet]
  (let [kohdeosat (into []
                        kohdeosa-xf
                        (hae-urakan-yllapitokohteiden-yllapitokohdeosat
                          db {:idt (map :id yllapitokohteet)}))]
    (mapv
      (fn [yllapitokohde]
        (let [kohteen-kohdeosat (first (filter #(= (:yllapitokohde-id %) (:id yllapitokohteet))
                                               kohdeosat))]
          (assoc yllapitokohde :kohdeosat kohteen-kohdeosat)))
      yllapitokohteet)))


