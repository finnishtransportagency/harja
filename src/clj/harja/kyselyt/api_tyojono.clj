(ns harja.kyselyt.api-tyojono
  (:require
    [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/api_tyojono.sql"
            {:positional? true})