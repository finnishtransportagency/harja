(ns harja.palvelin.integraatiot.api.tyokalut.validointi_test
  (:require [clojure.test :refer [deftest is testing]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.pvm :as pvm]))

(defn tasmaa-poikkeus [{:keys [type virheet]} tyyppi koodi viesti]
  (and
    (= tyyppi type)
    (some (fn [virhe] (and (= koodi (:koodi virhe))
                        (if viesti
                          (.contains (:viesti virhe) viesti)
                          true)))
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



(deftest tarkista-aikavali
  (testing "Aikaväli OK"
    (is (nil? (validointi/tarkista-aikavali (pvm/iso-8601->pvm "2020-01-01") (pvm/iso-8601->pvm "2020-12-31") [1 :vuosi]))))

  (testing "Alkupvm ja loppupvm ovat samat"
    (is (thrown+? #(tasmaa-poikkeus
                     %
                     virheet/+viallinen-kutsu+
                     virheet/+virheelinen-aikavali+
                     "'alkupvm' on sama kuin 'loppupvm'")
          (validointi/tarkista-aikavali (pvm/iso-8601->pvm "2020-01-01") (pvm/iso-8601->pvm "2020-01-01") [1 :vuosi]))))

  (testing "Loppupvm on ennen alkupvm"
    (is (thrown+? #(tasmaa-poikkeus
                     %
                     virheet/+viallinen-kutsu+
                     virheet/+virheelinen-aikavali+
                     "'loppupvm' on ennen 'alkupvm'")
          (validointi/tarkista-aikavali (pvm/iso-8601->pvm "2022-02-02") (pvm/iso-8601->pvm "2020-01-01") [1 :vuosi]))))

  (testing "Aikaväli on liian iso"
    (is (thrown+? #(tasmaa-poikkeus
                     %
                     virheet/+viallinen-kutsu+
                     virheet/+virheelinen-aikavali+
                     "Annettu aikaväli on liian suuri.")
          (validointi/tarkista-aikavali (pvm/iso-8601->pvm "2020-01-01") (pvm/iso-8601->pvm "2022-02-02") [1 :vuosi])))))
