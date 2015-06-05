(ns harja.kyselyt.havainnot
  "Havaintoihin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/havainnot.sql")
