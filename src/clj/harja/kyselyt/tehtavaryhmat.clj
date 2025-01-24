(ns harja.kyselyt.tehtavaryhmat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tehtavaryhmat.sql"
  {:positional? true})

(declare hae-tehtavaryhma)
