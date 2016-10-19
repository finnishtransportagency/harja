(ns harja.kyselyt.maksuerat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/maksuerat.sql"
  {:positional? true})