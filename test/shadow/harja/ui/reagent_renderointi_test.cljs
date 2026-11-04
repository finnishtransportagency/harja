(ns harja.ui.reagent-renderointi-test
  (:require [cljs.test :refer-macros [deftest is]]
            [reagent.core :as r]
            [react-testing-library-cljs.reagent.fire-event :as fire-event]
            [react-testing-library-cljs.reagent.render :as render]
            [react-testing-library-cljs.screen :as screen]))

(defn laskuri []
  (let [arvo (r/atom 0)]
    (fn []
      [:button {:on-click #(swap! arvo inc)}
       (str "Arvo: " @arvo)])))

(deftest reagent-renderointi-ja-tapahtuma-toimivat
  (render/render! [laskuri])
  (let [nappi (screen/get-by-role "button" {:name "Arvo: 0"})]
    (is (some? nappi))
    (fire-event/click nappi)
    (is (some? (screen/get-by-role "button" {:name "Arvo: 1"})))))