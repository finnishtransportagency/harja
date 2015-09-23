(ns harja.kyselyt.urakan-toimenpiteet
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/urakan_toimenpiteet.sql")

(defn hae-urakan-toimenpiteet-ja-tehtavat-tasot
  "Palauttaa hae-urakan-toimenpiteet-ja-tehtavat kyselyn tulokset tasoittain. Jokainen rivi on 
  vektori, joka sisältää 4 elementtiä, taso 1. - taso 4."
  [db urakka]
  (into []
        (map (fn [rivi]
               [{:id (:t1_id rivi) :nimi (:t1_nimi rivi) :koodi (:t1_koodi rivi) :taso 1}
                {:id (:t2_id rivi) :nimi (:t2_nimi rivi) :koodi (:t2_koodi rivi) :taso 2}
                {:id (:t3_id rivi) :nimi (:t3_nimi rivi) :koodi (:t3_koodi rivi) :taso 3}
                {:id (:t4_id rivi) :nimi (:t4_nimi rivi) :taso 4 :yksikko (:t4_yksikko rivi) :kokonaishintainen (:t4_kokonaishintainen rivi)}]))
        (hae-urakan-toimenpiteet-ja-tehtavat db urakka)))
