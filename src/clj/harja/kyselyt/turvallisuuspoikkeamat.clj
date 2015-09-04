(ns harja.kyselyt.turvallisuuspoikkeamat
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/turvallisuuspoikkeamat.sql")

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja]
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja))))