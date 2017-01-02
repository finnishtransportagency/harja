(ns harja.kyselyt.toimenpideinstanssit
  "Toimenpideinstansseihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/toimenpideinstanssit.sql"
  {:positional? true})

(defn onko-tuotu-samposta? [db sampo_toimenpidekoodi sampo-toimenpide-id urakka_sampoid]
  (:exists (first (onko-tuotu-samposta db sampo_toimenpidekoodi sampo-toimenpide-id urakka_sampoid))))

(defn onko-urakalla-toimenpide? [db urakkaid toimenpide]
  (:exists (first (onko-urakalla-toimenpide db urakkaid toimenpide))))
