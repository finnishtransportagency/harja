(ns harja.kyselyt.reikapaikkaukset
  (:require [jeesql.core :refer [defqueries]]))

  (defqueries "harja/kyselyt/reikapaikkaukset.sql"
    {:positional? false}) 
