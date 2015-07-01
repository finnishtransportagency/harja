(ns harja.kyselyt.havainnot
  "Havaintoihin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/havainnot.sql")

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id]
  (:exists (first (harja.kyselyt.havainnot/onko-olemassa-ulkoisella-idlla db ulkoinen-id))))
