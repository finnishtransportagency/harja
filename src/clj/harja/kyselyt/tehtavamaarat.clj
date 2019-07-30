(ns harja.kyselyt.tehtavamaarat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tehtavamaarat.sql"
  {:positional? false})
