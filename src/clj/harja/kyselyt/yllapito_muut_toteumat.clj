(ns harja.kyselyt.yllapito-muut-toteumat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/yllapito_toteumat.sql"
  {:positional? true})
