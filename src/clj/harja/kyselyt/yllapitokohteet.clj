(ns harja.kyselyt.yllapitokohteet
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/yllapitokohteet.sql")

(defn onko-olemassa-paallystysilmoitus? [db yllapitokohde-id]
  (:exists (first (harja.kyselyt.yllapitokohteet/yllapitokohteella-paallystysilmoitus
                    db
                    {:yllapitokohde yllapitokohde-id}))))

(defn onko-olemassa-paikkausilmioitus? [db yllapitokohde-id]
  (:exists (first (harja.kyselyt.yllapitokohteet/yllapitokohteella-paikkausilmoitus
                    db
                    {:yllapitokohde yllapitokohde-id}))))