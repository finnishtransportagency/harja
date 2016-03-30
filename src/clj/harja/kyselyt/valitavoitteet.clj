(ns harja.kyselyt.valitavoitteet
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/valitavoitteet.sql"
  {:positional? true})
