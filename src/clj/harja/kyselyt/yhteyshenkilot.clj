(ns harja.kyselyt.yhteyshenkilot
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/yhteyshenkilot.sql")

(defn onko-olemassa-yhteyshenkilo-ulkoisella-idlla? [db ulkoinen-id]
  (:exists (first (onko-olemassa-yhteyshenkilo-ulkoisella-idlla db ulkoinen-id))))