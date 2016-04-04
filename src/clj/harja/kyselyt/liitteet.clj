(ns harja.kyselyt.liitteet
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/liitteet.sql"
  {:positional? true})
