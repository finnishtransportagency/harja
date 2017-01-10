(ns harja.kyselyt.paikkaus
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/paikkaus.sql"
  {:positional? true})

(defn onko-olemassa-paikkausilmioitus? [db yllapitokohde-id]
  (:exists (first (harja.kyselyt.paikkaus/yllapitokohteella-paikkausilmoitus
                    db
                    {:yllapitokohde yllapitokohde-id}))))