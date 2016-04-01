(ns harja.kyselyt.kokonaishintaiset-tyot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kokonaishintaiset_tyot.sql"
  {:positional? true})
