(ns harja.kyselyt.toimenpideinstanssit
  "Toimenpideinstansseihin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/toimenpideinstanssit.sql")

(defn onko-tuotu-samposta? [db sampo_toimenpidekoodi sampo-toimenpide-id urakka_sampoid]
  (:exists (first (harja.kyselyt.toimenpideinstanssit/onko-tuotu-samposta db sampo_toimenpidekoodi sampo-toimenpide-id urakka_sampoid))))

(defn onko-urakka-hoito-urakka? [db urakka_sampoid]
  (:exists (first (harja.kyselyt.urakat/onko-urakka-hoidon-urakka db urakka_sampoid))))