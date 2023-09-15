(ns harja.kyselyt.info
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/info.sql"
  {:positional? true})
