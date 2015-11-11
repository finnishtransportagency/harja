(ns harja.kyselyt.sopimukset
  "Sopimuksiin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/sopimukset.sql")

(defn onko-olemassa? [db urakka-id sopimus-id]
  (:exists (first (harja.kyselyt.sopimukset/onko-olemassa db urakka-id sopimus-id))))