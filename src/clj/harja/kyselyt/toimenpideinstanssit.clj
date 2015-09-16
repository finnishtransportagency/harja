(ns harja.kyselyt.toimenpideinstanssit
  "Toimenpideinstansseihin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/toimenpideinstanssit.sql")

(defn onko-tuotu-samposta? [db sampo_toimenpidekoodi urakka_sampoid]
  (:exists (first (harja.kyselyt.toimenpideinstanssit/onko-tuotu-samposta db sampo_toimenpidekoodi urakka_sampoid))))