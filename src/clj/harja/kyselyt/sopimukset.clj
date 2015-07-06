(ns harja.kyselyt.sopimukset
  "Sopimuksiin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/sopimukset.sql")