(ns harja.kyselyt.hoitoluokat
  "Havaintoihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/hoitoluokat.sql"
  {:positional? true})
