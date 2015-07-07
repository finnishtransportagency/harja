(ns harja.kyselyt.toimenpideinstanssit
  "Toimenpideinstansseihin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/toimenpideinstanssit.sql")