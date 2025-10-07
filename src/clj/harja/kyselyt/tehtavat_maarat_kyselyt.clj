(ns harja.kyselyt.tehtavat-maarat-kyselyt
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tehtavat_maarat_kyselyt.sql"
  {:positional? true})

(declare hae-maaramitattavat-tehtavat)
