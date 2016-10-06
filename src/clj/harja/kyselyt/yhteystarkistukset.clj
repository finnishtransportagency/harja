(ns harja.kyselyt.yhteystarkistukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/yhteystarkistukset.sql"
  {:positional? true})