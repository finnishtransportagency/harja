(ns harja.kyselyt.kulut
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kulut.sql"
  {:positional? false})
