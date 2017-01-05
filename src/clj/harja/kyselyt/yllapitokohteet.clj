(ns harja.kyselyt.yllapitokohteet
  (:require [jeesql.core :refer [defqueries]]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/yllapitokohteet.sql")

(def kohdeosa-xf (geo/muunna-pg-tulokset :sijainti))

(defn liita-kohdeosat-kohteelle [db yllapitokohde]
  (assoc yllapitokohde
    :kohdeosat
    (into []
          kohdeosa-xf
          (harja.kyselyt.paallystys/hae-urakan-yllapitokohteen-yllapitokohdeosat
            db {:yllapitokohde (:id yllapitokohde)}))))

(defn onko-olemassa-paallystysilmoitus? [db yllapitokohde-id]
  (:exists (first (harja.kyselyt.yllapitokohteet/yllapitokohteella-paallystysilmoitus
                    db
                    {:yllapitokohde yllapitokohde-id}))))

(defn onko-olemassa-paikkausilmioitus? [db yllapitokohde-id]
  (:exists (first (harja.kyselyt.yllapitokohteet/yllapitokohteella-paikkausilmoitus
                    db
                    {:yllapitokohde yllapitokohde-id}))))