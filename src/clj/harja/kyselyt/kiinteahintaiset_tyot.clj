(ns harja.kyselyt.kiinteahintaiset-tyot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kiinteahintaiset_tyot.sql"
  {:positional? true})
