(ns harja.palvelin.palvelut.urakan-toimenpiteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))

(deftest hae-urakan-1-toimenpiteet []
    (let [db (apply tietokanta/luo-tietokanta testitietokanta)
        user "harja"
        urakka-id 1]
    (is (not (nil? (urakan-toimenpiteet/hae-urakan-toimenpiteet db user urakka-id))))
    (is (= (count (urakan-toimenpiteet/hae-urakan-toimenpiteet db user urakka-id)) 2))
    (is (= (:t3_nimi (first (urakan-toimenpiteet/hae-urakan-toimenpiteet db user urakka-id))) "Laaja toimenpide"))
    (is (= (:id (first (urakan-toimenpiteet/hae-urakan-toimenpiteet db user urakka-id))) 911))
    (is (= (:tpi_nimi (first (urakan-toimenpiteet/hae-urakan-toimenpiteet db user urakka-id))) "Oulu Talvihoito TP"))
    (is (= (:tpi_nimi (second (urakan-toimenpiteet/hae-urakan-toimenpiteet db user urakka-id))) "Oulu Sorateiden hoito TP"))))


(deftest hae-urakan-1-toimenpiteet-ja-tehtavat []
    (let [db (apply tietokanta/luo-tietokanta testitietokanta)
        user "harja"
        urakka-id 1]
    (is (not (nil? (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat db user urakka-id))))))