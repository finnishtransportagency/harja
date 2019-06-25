(ns harja.kyselyt.tyokoneseuranta
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tyokoneseuranta.sql"
  {:positional? false})
