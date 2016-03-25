(ns harja.kyselyt.tieverkko
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tieverkko.sql"
  {:positional? true})
