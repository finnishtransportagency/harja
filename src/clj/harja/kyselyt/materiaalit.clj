(ns harja.kyselyt.materiaalit
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/materiaalit.sql"
  {:positional? true})
