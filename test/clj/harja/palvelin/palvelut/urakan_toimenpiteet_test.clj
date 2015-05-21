(ns harja.palvelin.palvelut.urakan-toimenpiteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))

(deftest hae-urakan-1-toimenpiteet
  (let [db (apply tietokanta/luo-tietokanta testitietokanta)
        user "harja"
        urakka-id 1
        response (urakan-toimenpiteet/hae-urakan-toimenpiteet db user urakka-id)]
    (is (not (nil? response)))
    (is (= (count response) 2))
    (is (= (:t3_nimi (first response)) "Laaja toimenpide"))
    (is (= (:id (first response)) 911))
    (is (= (:tpi_nimi (first response)) "Oulu Talvihoito TP"))
    (is (= (:tpi_nimi (second response)) "Oulu Sorateiden hoito TP"))))


(deftest hae-urakan-1-toimenpiteet-ja-tehtavat
  (let [db (apply tietokanta/luo-tietokanta testitietokanta)
        user "harja"
        urakka-id 1
        response (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat db user urakka-id)]
    (is (not (nil? response)))))
