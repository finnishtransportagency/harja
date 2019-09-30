(ns harja.kyselyt.laskut
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/laskut.sql"
  {:positional? false})
