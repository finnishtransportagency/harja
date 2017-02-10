(ns harja-laadunseuranta.ui.ylapalkki-test
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [reagent.core :as reagent :refer [atom]]
            [dommy.core :as dommy]
            [harja.testutils.shared-testutils :refer [sel sel1]]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.ui.ylapalkki :as ylapalkki]
            [cljs-react-test.utils])
  (:require-macros [harja-laadunseuranta.test-macros :refer [with-component prepare-component-tests]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(prepare-component-tests)

(deftest ylapalkki-test
  (testing "Yläpalkki näyttää tierekisteriosoitteen oikein"
    (let [tr-osoite (atom {:tie 20
                           :aosa 4
                           :aet 3000})]
      (with-component [ylapalkki/ylapalkkikomponentti
                       {:hoitoluokka             "Ia"
                        :soratiehoitoluokka      "5"
                        :tr-osoite               @tr-osoite
                        :tarkastusajo-alkamassa? false
                        :tarkastusajo-kaynnissa? false
                        :disabloi-kaynnistys?    false
                        :palvelinvirhe           nil
                        :havaintonappi-painettu  false
                        :nayta-paanavigointi?    true
                        :piirra-paanavigointi?   true
                        :havaintolomake-auki?    false
                        :kaynnista-tarkastus-fn  (constantly #())
                        :pysayta-tarkastusajo-fn (constantly #())}]
        (let [palkki-div (sel1 [:div.tr-osoite])
              hoitoluokka-div (sel1 [:div.soratiehoitoluokka])
              talvihoitoluokka-div (sel1 [:div.talvihoitoluokka])]
          (is (= "20 / 4 / 3000" (dommy/text palkki-div)))
          (println "dommyn palkki" (pr-str palkki-div))
          (is (= "SHL: 5" (dommy/text hoitoluokka-div)))
          (is (= "THL: Ia" (dommy/text talvihoitoluokka-div))))))))
