(ns harja.kyselyt.vemtr
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/vemtr.sql"
  {:positional? true})
