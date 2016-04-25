(ns harja.kyselyt.yllapito
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/yllapito.sql"
  {:positional? true})
