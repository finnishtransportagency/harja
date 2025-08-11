(ns harja.kyselyt.velho-nimikkeistot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/velho_nimikkeistot.sql"
  {:positional? false})
