(ns harja.kyselyt.kommentit
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kommentit.sql"
  {:positional? true})
