(ns harja.kyselyt.hoitoluokat
  "Havaintoihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(declare hae-hoitoluokka-tr-pisteelle)

(defqueries "harja/kyselyt/hoitoluokat.sql"
  {:positional? true})
