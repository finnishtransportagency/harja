(ns harja.ui.viesti-test
  (:require [clojure.string :as str]
            [cljs.test :as t :refer-macros [deftest is]]
            [harja.testutils.shared-testutils :as u]
            [harja.ui.viesti :as viesti])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

(defn- laskettu-tyyliarvo [selector ominaisuus]
  (some-> (u/sel1 selector)
          js/getComputedStyle
          (.getPropertyValue ominaisuus)
          str/trim))

(deftest flash-viesti-renderoi-harjan-omat-primitiiviluokat
  (reset! viesti/viesti-sisalto {:viesti "Tallennus onnistui"
                                 :luokka :success
                                 :nakyvissa? true
                                 :kesto 999999})
  (komponenttitesti
    [viesti/viesti-container]

    (is (= 1 (count (u/sel "[data-cy=\"flash-viesti-overlay\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"flash-viesti-tausta\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"flash-viesti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"flash-viesti-sisalto\"]"))))
    (is (= 1 (count (u/sel [:.harja-viesti]))))
    (is (= 0 (count (u/sel [:.alert])))))
  (reset! viesti/viesti-sisalto {:viesti nil :luokka nil :nakyvissa? false :kesto nil}))

(deftest flash-viestin-komponenttikohtainen-token-voittaa-yhteisen-teematokenin
  (reset! viesti/viesti-sisalto {:viesti "Tokenitesti"
                                 :luokka :info
                                 :nakyvissa? true
                                 :kesto 999999})
  (komponenttitesti
    [:div {:style {"--harja-teema-pinta" "#ddeeff"
                   "--harja-teema-reuna" "#112233"
                   "--harja-teema-teksti" "rgb(17, 18, 19)"
                   "--harja-teema-radius" "0"
                   "--harja-teema-varjo" "none"
                   "--harja-teema-sisalto-padding-pysty" "0.5rem"
                   "--harja-teema-sisalto-padding-vaaka" "1.5rem"
                   "--harja-viesti-pinta" "rgb(255, 238, 170)"
                   "--harja-viesti-reuna" "rgb(10, 20, 30)"
                   "--harja-viesti-teksti" "rgb(33, 34, 35)"
                   "--harja-viesti-radius" "12px"
                   "--harja-viesti-varjo" "0 0 0 3px rgb(1, 2, 3)"
                   "--harja-viesti-padding-pysty" "0.75rem"
                   "--harja-viesti-padding-vaaka" "2rem"}}
     [viesti/viesti-container]]

    (is (= "rgb(255, 238, 170)" (laskettu-tyyliarvo "[data-cy=\"flash-viesti-sisalto\"]" "background-color")))
    (is (= "rgb(10, 20, 30)" (laskettu-tyyliarvo "[data-cy=\"flash-viesti-sisalto\"]" "border-top-color")))
    (is (= "rgb(33, 34, 35)" (laskettu-tyyliarvo "[data-cy=\"flash-viesti-sisalto\"]" "color")))
    (is (= "12px" (laskettu-tyyliarvo "[data-cy=\"flash-viesti-sisalto\"]" "border-top-left-radius")))
    (is (not= "none" (laskettu-tyyliarvo "[data-cy=\"flash-viesti-sisalto\"]" "box-shadow")))
    (is (= "12px" (laskettu-tyyliarvo "[data-cy=\"flash-viesti-sisalto\"]" "padding-top")))
    (is (= "32px" (laskettu-tyyliarvo "[data-cy=\"flash-viesti-sisalto\"]" "padding-right"))))
  (reset! viesti/viesti-sisalto {:viesti nil :luokka nil :nakyvissa? false :kesto nil}))

(deftest flash-viestin-yhteinen-teematoken-vaikuttaa-vaara-varianttiin
  (reset! viesti/viesti-sisalto {:viesti "Virhe"
                                 :luokka :danger
                                 :nakyvissa? true
                                 :kesto 999999})
  (komponenttitesti
    [:div {:style {"--harja-teema-pinta" "rgb(255, 238, 170)"
                   "--harja-teema-reuna" "rgb(10, 20, 30)"
                   "--harja-teema-teksti" "rgb(33, 34, 35)"
                   "--harja-teema-varjo" "none"}}
     [viesti/viesti-container]]

    (is (= "rgb(255, 238, 170)" (laskettu-tyyliarvo "[data-cy=\"flash-viesti-sisalto\"]" "background-color")))
    (is (= "rgb(10, 20, 30)" (laskettu-tyyliarvo "[data-cy=\"flash-viesti-sisalto\"]" "border-top-color")))
    (is (= "rgb(33, 34, 35)" (laskettu-tyyliarvo "[data-cy=\"flash-viesti-sisalto\"]" "color")))
    (is (= "none" (laskettu-tyyliarvo "[data-cy=\"flash-viesti-sisalto\"]" "box-shadow"))))
  (reset! viesti/viesti-sisalto {:viesti nil :luokka nil :nakyvissa? false :kesto nil}))
