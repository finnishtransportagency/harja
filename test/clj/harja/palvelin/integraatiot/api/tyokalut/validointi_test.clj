(ns harja.palvelin.integraatiot.api.tyokalut.validointi_test
  (:require [clojure.test :refer :all]
            [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]))

(defn tasmaa-poikkeus [{:keys [type virheet]} tyyppi koodi viesti]
  (and
    (= tyyppi type)
    (some (fn [virhe] (and (= koodi (:koodi virhe)) (.contains (:viesti virhe) viesti)))
          virheet)))

(deftest onko-liikenneviraston-jarjestelma
  (let [db (luo-testitietokanta)]
    (is (thrown+?
          #(tasmaa-poikkeus
             %
             virheet/+kayttajalla-puutteelliset-oikeudet+
             virheet/+kayttajalla-puutteelliset-oikeudet+
             "Käyttäjällä ei resurssiin.")
          (validointi/tarkista-onko-liikenneviraston-jarjestelma db +kayttaja-jvh+)))
    (validointi/tarkista-onko-liikenneviraston-jarjestelma db +livi-jarjestelma-kayttaja+)))





