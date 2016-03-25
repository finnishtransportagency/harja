(ns harja.kyselyt.paikkaus
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/paikkaus.sql"
  {:positional? true})
