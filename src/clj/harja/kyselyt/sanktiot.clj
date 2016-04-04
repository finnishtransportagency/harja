(ns harja.kyselyt.sanktiot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/sanktiot.sql"
  {:positional? true})
