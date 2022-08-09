(ns harja.kyselyt.suolarajoitus-kyselyt
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/suolarajoitus_kyselyt.sql"
  {:positional? true})
