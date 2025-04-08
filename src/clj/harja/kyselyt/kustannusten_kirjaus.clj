(ns harja.kyselyt.kustannusten-kirjaus
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kustannusten_kirjaus.sql"
  {:positional? true})
