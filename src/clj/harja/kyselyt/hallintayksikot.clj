(ns harja.kyselyt.hallintayksikot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/hallintayksikot.sql"
  {:positional? true})
