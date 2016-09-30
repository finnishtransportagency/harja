(ns harja-laadunseuranta.ylapalkki-test
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [reagent.core :as reagent :refer [atom]]
            [dommy.core :as dommy]
            [harja-laadunseuranta.testutils :refer [sel sel1]]
            [harja-laadunseuranta.asetukset :as asetukset]
            [harja-laadunseuranta.ylapalkki :as ylapalkki]
            [cljs-react-test.utils])
  (:require-macros [harja-laadunseuranta.test-macros :refer [with-component prepare-component-tests]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(prepare-component-tests)

(deftest ylapalkki-test
  (testing "Yläpalkki näyttää tierekisteriosoitteen oikein"
    (let [tr-osoite (atom {:tie 20
                           :aosa 4
                           :aet 3000})
          kuva (atom nil)]
      (with-component [ylapalkki/ylapalkkikomponentti
                       {:tiedot-nakyvissa (atom false)
                        :hoitoluokka (atom "Ia")
                        :soratiehoitoluokka (atom "5")
                        :tr-osoite tr-osoite
                        :kiinteistorajat (atom false)
                        :ortokuva (atom false)
                        :tallennus-kaynnissa (atom false)
                        :tallennustilaa-muutetaan kuva
                        :keskita-ajoneuvoon (atom false)
                        :disabloi-kaynnistys? (atom false)
                        :valittu-urakka (atom {:nimi "Foo" :id 666})
                        :palvelinvirhe nil}]
        (let [palkki-div (sel1 [:div.tr-osoite])
              hoitoluokka-div (sel1 [:div.soratiehoitoluokka])
              talvihoitoluokka-div (sel1 [:div.talvihoitoluokka])]
          (is (= "20 / 4 / 3000" (dommy/text palkki-div)))
          (is (= "SHL: 5" (dommy/text hoitoluokka-div)))
          (is (= "THL: Ia" (dommy/text talvihoitoluokka-div))))))))
