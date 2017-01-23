(ns harja.kyselyt.yllapitokohteet
  (:require [jeesql.core :refer [defqueries]]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/yllapitokohteet.sql")

(def kohdeosa-xf (geo/muunna-pg-tulokset :sijainti))

(defn liita-kohdeosat [db map yllapitokohde-id]
  (assoc map
    :kohdeosat
    (into []
          kohdeosa-xf
          (hae-urakan-yllapitokohteen-yllapitokohdeosat
            db {:yllapitokohde yllapitokohde-id}))))


