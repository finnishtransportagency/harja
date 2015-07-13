(ns harja.kyselyt.toteumat
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/toteumat.sql")

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja]
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja))))