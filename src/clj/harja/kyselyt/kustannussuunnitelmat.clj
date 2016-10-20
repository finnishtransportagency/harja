(ns harja.kyselyt.kustannussuunnitelmat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kustannussuunnitelmat.sql"
  {:positional? true})