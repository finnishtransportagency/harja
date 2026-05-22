(ns harja.ui.modal-test
  (:require [clojure.string :as str]
            [cljs.test :as t :refer-macros [deftest is]]
            [harja.testutils.shared-testutils :as u]
            [harja.ui.modal :as modal])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

(defn- laskettu-tyyliarvo [selector ominaisuus]
  (some-> (u/sel1 selector)
          js/getComputedStyle
          (.getPropertyValue ominaisuus)
          str/trim))

(deftest modaali-renderoi-harjan-omat-primitiiviluokat
  (komponenttitesti
    [modal/modal {:otsikko "Modaalin otsikko"
                  :nakyvissa? true
                  :footer [:button {:type "button"} "Sulje"]}
     [:div "Modaalin sisältö"]]

    (is (= 1 (count (u/sel [:.harja-modal]))))
    (is (= 1 (count (u/sel [:.harja-modal-tausta]))))
    (is (= 1 (count (u/sel [:.harja-modal-dialogi]))))
    (is (= 1 (count (u/sel [:.harja-modal-sisalto]))))
    (is (= 1 (count (u/sel [:.harja-modal-otsake]))))
    (is (= 1 (count (u/sel [:.harja-modal-otsakerivi]))))
    (is (= 1 (count (u/sel [:.harja-modal-runko]))))
    (is (= 1 (count (u/sel [:.harja-modal-alatunniste]))))
    (is (= 1 (count (u/sel [:.harja-modal-otsikko]))))
    (is (= 1 (count (u/sel [:.harja-modal-sulje]))))

    (is (= 0 (count (u/sel [:.modal-dialog]))))
    (is (= 0 (count (u/sel [:.modal-content]))))
    (is (= 0 (count (u/sel [:.modal-header]))))
    (is (= 0 (count (u/sel [:.modal-body]))))
    (is (= 0 (count (u/sel [:.modal-footer]))))))

(deftest modaali-renderoi-otsikon-kun-otsikolla-on-margin-bottom-muotoilu
  (komponenttitesti
    [modal/modal {:otsikko "Tyylitelty otsikko"
                  :otsikko-muotoilut {:font-size "28px" :margin-bottom "24px"}
                  :nakyvissa? true}
     [:div "Modaalin sisältö"]]

    (is (= "Tyylitelty otsikko" (u/text [:.harja-modal-otsikko])))
    (is (= 1 (count (u/sel [:.harja-modal-sulje]))))
    (is (str/includes? (or (.getAttribute (u/sel1 [:.harja-modal-otsakerivi]) "style") "")
                      "margin-bottom: 24px"))
    (is (not (str/includes? (or (.getAttribute (u/sel1 [:.harja-modal-otsikko]) "style") "")
                           "margin-bottom")))))

(deftest modaali-komponenttikohtainen-token-voittaa-yhteisen-teematokenin
  (komponenttitesti
    [:div {:style {"--harja-teema-pinta" "#ddeeff"
                   "--harja-teema-reuna" "#112233"
                   "--harja-teema-teksti" "rgb(17, 18, 19)"
                   "--harja-teema-radius" "0"
                   "--harja-teema-varjo" "0 0 0 3px rgb(1, 2, 3)"
                   "--harja-modal-pinta" "rgb(255, 238, 170)"
                   "--harja-modal-reuna" "rgb(10, 20, 30)"
                   "--harja-modal-teksti" "rgb(33, 34, 35)"
                   "--harja-modal-radius" "12px"
                   "--harja-modal-varjo" "none"}}
     [modal/modal {:otsikko "Tokenitesti"
                   :nakyvissa? true}
      [:div "Modaalin sisältö"]]]

    (is (= "rgb(255, 238, 170)" (laskettu-tyyliarvo "[data-cy=\"harja-modal-sisalto\"]" "background-color")))
    (is (= "rgb(10, 20, 30)" (laskettu-tyyliarvo "[data-cy=\"harja-modal-sisalto\"]" "border-top-color")))
    (is (= "rgb(33, 34, 35)" (laskettu-tyyliarvo "[data-cy=\"harja-modal-sisalto\"]" "color")))
    (is (= "12px" (laskettu-tyyliarvo "[data-cy=\"harja-modal-sisalto\"]" "border-top-left-radius")))
    (is (= "none" (laskettu-tyyliarvo "[data-cy=\"harja-modal-sisalto\"]" "box-shadow")))))
