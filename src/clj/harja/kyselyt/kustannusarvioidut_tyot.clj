(ns harja.kyselyt.kustannusarvioidut-tyot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kustannusarvioidut_tyot.sql"
  {:positional? true})
