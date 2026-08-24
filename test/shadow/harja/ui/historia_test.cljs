(ns harja.ui.historia-test
  (:require [cljs.test :refer-macros [deftest is]]
            [harja.ui.historia :as historia]
            [reagent.core :as r]
            [react-testing-library-cljs.reagent.fire-event :as fire-event]
            [react-testing-library-cljs.reagent.render :as render]
            [react-testing-library-cljs.screen :as screen]))

(deftest kumoa
  (let [tila (r/atom {})
        historia-tila (historia/historia tila)
        lopeta! (historia/kuuntele! historia-tila)
        komponentti (fn []
                      [:div
                       [historia/kumoa historia-tila]
                       [:input {:value (:teksti @tila)
                                :on-change #(swap! tila assoc :teksti (-> % .-target .-value))}]
                       [:button {:on-click #(swap! tila update-in [:clicks] (fnil inc 0))}
                        (or (:clicks @tila) 0)]])]
    (render/render! [komponentti])
    (let [syote (screen/get-by-role "textbox")
          kumoa-nappi (screen/get-by-role "button" {:name "Kumoa"})]
      (is (true? (.-disabled kumoa-nappi)))

      (fire-event/change syote {:target {:value "foo"}})
      (is (= {:teksti "foo"} @tila))
      (is (false? (.-disabled kumoa-nappi)))

      (fire-event/change syote {:target {:value "bar"}})
      (fire-event/click (screen/get-by-role "button" {:name "0"}))
      (is (= {:teksti "bar" :clicks 1} @tila))

      (fire-event/click kumoa-nappi)
      (is (= {:teksti "bar"} @tila) "Klikkaus peruttu")
      (fire-event/click kumoa-nappi)
      (is (= {:teksti "foo"} @tila) "Bar-muutos peruttu")
      (fire-event/click kumoa-nappi)
      (is (= {} @tila) "Kaikki muutokset peruttu")
      (is (true? (.-disabled kumoa-nappi))))
    (lopeta!)))