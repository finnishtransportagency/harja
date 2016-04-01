(ns harja.kyselyt.tilannekuva
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tilannekuva.sql"
  {:positional? true})
