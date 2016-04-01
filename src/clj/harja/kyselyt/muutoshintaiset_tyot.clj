(ns harja.kyselyt.muutoshintaiset-tyot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/muutoshintaiset_tyot.sql"
  {:positional? true})
