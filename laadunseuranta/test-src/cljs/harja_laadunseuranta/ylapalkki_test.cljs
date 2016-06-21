(ns harja-laadunseuranta.ylapalkki-test
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [reagent.core :as reagent :refer [atom]]
            [dommy.core :as dommy]
            [harja-laadunseuranta.testutils :refer [sel sel1]]
            [harja-laadunseuranta.asetukset :as asetukset]
            [harja-laadunseuranta.ylapalkki :as ylapalkki])
  (:require-macros [harja-laadunseuranta.test-macros :refer [with-component prepare-component-tests]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(prepare-component-tests)

(deftest ylapalkki-test
  (testing "Yläpalkki näyttää tierekisteriosoitteen oikein"
    (let [tr-osoite (atom {:tie 20
                           :aosa 4
                           :aet 3000})
          kuva (atom nil)]
      (with-component [ylapalkki/ylapalkkikomponentti (atom false) (atom "Ia") (atom "5") tr-osoite "" (atom false) (atom false) (atom false) kuva]
        (let [palkki-div (sel1 [:div.tr-osoite])
              hoitoluokka-div (sel1 [:div.soratiehoitoluokka])
              talvihoitoluokka-div (sel1 [:div.talvihoitoluokka])]
          (is (= "20 / 4 / 3000" (dommy/text palkki-div)))
          (is (= "Soratiehoitoluokka: 5" (dommy/text hoitoluokka-div)))
          (is (= "Talvihoitoluokka: Ia" (dommy/text talvihoitoluokka-div))))))))

