(ns harja.kyselyt.lampotilat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/lampotilat.sql"
  {:positional? true})
