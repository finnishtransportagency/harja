(ns harja.kyselyt.turvallisuuspoikkeamat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/turvallisuuspoikkeamat.sql"
  {:positional? true})

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja]
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja))))
