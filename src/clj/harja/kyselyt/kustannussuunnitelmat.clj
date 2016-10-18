(ns harja.kyselyt.kustannussuunnitelmat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kustannussuunnitelmat.sql"
  {:positional? true})

(defn onko-olemassa? [db numero]
  (:exists (first (onko-olemassa db numero))))