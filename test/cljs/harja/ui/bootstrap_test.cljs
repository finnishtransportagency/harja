(ns harja.ui.bootstrap-test
  (:require [cljs.test :as t :refer-macros [deftest is]]
            [reagent.core :as r]
            [harja.testutils.shared-testutils :as u]
            [harja.ui.bootstrap :as bootstrap])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

(deftest panel-poistaa-bootstrap-markupin-ja-sailyttaa-api:n
  (komponenttitesti
    [bootstrap/panel {:data-cy "testi-paneli"}
     "Otsikko"
     [:div "Sisalto"]]
    (is (= 1 (count (u/sel "[data-cy=\"testi-paneli\"].harja-panel"))))
    (is (= 1 (count (u/sel "[data-cy=\"testi-paneli\"] .harja-panel-otsake"))))
    (is (= 0 (count (u/sel "[data-cy=\"testi-paneli\"] .panel"))))))

(deftest tabs-vaihtaa-valilehtea-ja-kayttaa-vakaita-ankkureita
  (let [aktiivinen (r/atom :perustiedot)]
    (komponenttitesti
      [bootstrap/tabs {:active aktiivinen :classes "tabs-taso1"}
       {:teksti "Perustiedot" :data-cy "testi-perustiedot"} :perustiedot [:div "Perustiedot"]
       {:teksti "Historia" :data-cy "testi-historia"} :historia [:div "Historia"]]
      (is (= 1 (count (u/sel "[data-cy=\"testi-perustiedot\"]"))))
      (is (= 0 (count (u/sel ".nav-tabs"))))
      (u/click "[data-cy=\"testi-historia\"]")
      (is (= :historia @aktiivinen))
      (is (= "Historia" (u/text ".harja-tabs-sisalto"))))))

(deftest navbar-vaihtaa-mobiilisisallon-tilaa
  (komponenttitesti
    [bootstrap/navbar {:data-cy "testi-navbar"}
     "Harja"
     [:a {:href "#"} "Vasen"]
     :right
     [:a {:href "#"} "Oikea"]]
    (is (= 1 (count (u/sel "[data-cy=\"testi-navbar\"].harja-navbar"))))
    (is (= "false" (.getAttribute (u/sel1 "[data-cy=\"testi-navbar-vaihtaja\"]") "aria-expanded")))
    (u/click "[data-cy=\"testi-navbar-vaihtaja\"]")
    (is (= 1 (count (u/sel ".harja-navbar-sisalto-avoin"))))
    (is (= 0 (count (u/sel ".navbar"))))))
