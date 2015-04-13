(ns harja.kyselyt.urakan-toimenpiteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.kyselyt.urakan-toimenpiteet :as urakan-toimenpiteet]
            [taoensso.timbre :as log]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))

(deftest hae-urakan-1-toimenpiteet-ja-tehtavat-tasot []
    (let [db (apply tietokanta/luo-tietokanta testitietokanta)
         urakka-id 1
         response (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat-tasot db urakka-id)]
    (log/info response)
    (is (not (nil? response)))
    (is (= (count response) 4))

    (mapv (fn [rivi]
              (is (= (:taso (first rivi)) 1))
              (is (= (:id (first rivi)) 906)))
              response)

    (mapv (fn [rivi] (is (= (:taso (nth rivi 1)) 2))) response)
    (mapv (fn [rivi] (is (= (:taso (nth rivi 2)) 3))) response)
    (mapv (fn [rivi] (is (= (:taso (nth rivi 3)) 4))) response)))