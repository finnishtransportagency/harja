(ns harja.kyselyt.yllapitokohteet
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/yllapitokohteet.sql")

(def kohdeosa-xf (geo/muunna-pg-tulokset :sijainti))

(defn liita-kohdeosat-kohteisiin [db kohteet kohde-id-avain]
  (let [kohdeosat (into []
                        kohdeosa-xf
                        (hae-urakan-yllapitokohteiden-yllapitokohdeosat
                          db {:idt (map kohde-id-avain kohteet)}))]
    (mapv
      (fn [kohde]
        (let [kohteen-kohdeosat (filterv #(= (:yllapitokohde-id %) (kohde-id-avain kohde)) kohdeosat)]
          (assoc kohde :kohdeosat kohteen-kohdeosat)))
      kohteet)))