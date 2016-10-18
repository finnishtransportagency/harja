(ns harja.kyselyt.maksuerat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/maksuerat.sql"
  {:positional? true})

(defn onko-olemassa? [db numero]
  (:exists (first (onko-olemassa db numero))))