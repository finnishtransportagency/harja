(ns harja.kyselyt.ilmoitukset
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/ilmoitukset.sql")

(defn onko-olemassa? [db ilmoitusid]
  (:exists (first (harja.kyselyt.ilmoitukset/onko-olemassa db ilmoitusid))))
