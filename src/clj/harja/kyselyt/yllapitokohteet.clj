(ns harja.kyselyt.yllapitokohteet
  (:require [jeesql.core :refer [defqueries]]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/yllapitokohteet.sql")

(def kohdeosa-xf (geo/muunna-pg-tulokset :sijainti))

(defn liita-kohdeosat-kohteisiin [db kohteet id-avain]
  (let [kohdeosat (into []
                        kohdeosa-xf
                        (hae-urakan-yllapitokohteiden-yllapitokohdeosat
                          db {:idt (map id-avain kohteet)}))]
    (mapv
      (fn [yllapitokohde]
        (let [kohteen-kohdeosat (first (filter #(= (:yllapitokohde-id %) (id-avain kohteet))
                                               kohdeosat))]
          (assoc yllapitokohde :kohdeosat kohteen-kohdeosat)))
      kohteet)))


