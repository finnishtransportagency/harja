(ns harja.kyselyt.budjettisuunnittelu
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/budjettisuunnittelu.sql"
  {:positional? false})
