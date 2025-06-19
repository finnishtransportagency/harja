(ns harja.kyselyt.sopimukset
  "Sopimuksiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/sopimukset.sql"
  {:positional? true})

(declare hae-urakan-paasopimus)

(defn onko-olemassa? [db urakka-id sopimus-id]
  (:exists (first (harja.kyselyt.sopimukset/onko-olemassa db urakka-id sopimus-id))))
