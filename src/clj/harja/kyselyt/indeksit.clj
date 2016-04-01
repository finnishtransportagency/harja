(ns harja.kyselyt.indeksit
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/indeksit.sql"
  {:positional? true})
