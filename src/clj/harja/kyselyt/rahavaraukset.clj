(ns harja.kyselyt.rahavaraukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/rahavaraukset.sql"
  {:positional? true})
