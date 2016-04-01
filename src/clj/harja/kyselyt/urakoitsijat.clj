(ns harja.kyselyt.urakoitsijat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/urakoitsijat.sql"
  {:positional? true})
