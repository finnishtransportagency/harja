(ns harja.kyselyt.reikapaikkaukset
  (:require [jeesql.core :refer [defqueries]]
            [harja.geo :as geo]))

  (defqueries "harja/kyselyt/reikapaikkaukset.sql"
    {:positional? false}) 
