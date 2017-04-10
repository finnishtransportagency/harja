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
      (fn [kohde]
        (let [kohteen-kohdeosat (filterv #(= (:yllapitokohde-id %) (id-avain kohteet)) kohdeosat)]
          (assoc kohde :kohdeosat kohteen-kohdeosat)))
      kohteet)))


