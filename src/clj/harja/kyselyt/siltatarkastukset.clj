(ns harja.kyselyt.siltatarkastukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/siltatarkastukset.sql"
  {:positional? true})
