(ns harja.kyselyt.ilmoitukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/ilmoitukset.sql"
            {:positional? true})
