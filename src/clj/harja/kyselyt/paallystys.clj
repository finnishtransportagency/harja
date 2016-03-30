(ns harja.kyselyt.paallystys
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/paallystys.sql"
  {:positional? true})
