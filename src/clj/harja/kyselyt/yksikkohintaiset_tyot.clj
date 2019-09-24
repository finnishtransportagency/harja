(ns harja.kyselyt.yksikkohintaiset-tyot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/yksikkohintaiset_tyot.sql"
  {:positional? false})
