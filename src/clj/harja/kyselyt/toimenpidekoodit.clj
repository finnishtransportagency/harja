(ns harja.kyselyt.toimenpidekoodit
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/toimenpidekoodit.sql"
  {:positional? true})
