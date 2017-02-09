(ns harja.ui.historia-test
  (:require [harja.ui.historia :as historia]
            [cljs.test :as test :refer-macros [deftest is testing async]]
            [harja.testutils.shared-testutils :refer [komponentti-fixture render change click
                                                      paivita sel1 disabled?]]
            [reagent.core :as r]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(test/use-fixtures :each komponentti-fixture)

(deftest kumoa
  (let [tila (r/atom {})
        historia (historia/historia tila)
        lopeta! (historia/kuuntele! historia)
        comp (fn []
               [:div
                [historia/kumoa historia]
                [:input#teksti {:value (:teksti @tila)
                                :on-change #(swap! tila assoc :teksti (-> % .-target .-value))}]
                [:button#nappi {:on-click #(swap! tila update-in [:clicks] (fnil inc 0))}
                 (or (:clicks @tila) 0)]])]
    (async
      done

      (go
        (render [comp])

        ;; Alkutilassa ei historiaa, kumoa nappi disabled
        (is (disabled? :.kumoa-nappi))

        ;; Muutetaan tekstiä
        (change :#teksti "foo")
        (<! (paivita))

        ;; Teksti on muuttunut
        (is (= {:teksti "foo"} @tila))
        (is (not (disabled? :.kumoa-nappi)))
        (is (historia/voi-kumota? historia))

        ;; Muutetaan tekstiä ja painetaan nappia
        (change :#teksti "bar")
        (click :#nappi)
        (<! (paivita))
        (is (= {:teksti "bar" :clicks 1} @tila))

        ;; Perutaan yksi muutos
        (click :.kumoa-nappi)
        (<! (paivita))
        (is (= {:teksti "bar"} @tila) "Klikkaus peruttu")

        ;; Perutaan toinen muutos
        (click :.kumoa-nappi)
        (<! (paivita))
        (is (= {:teksti "foo"} @tila) "Bar muutos peruttu")

        ;; Perutaan viimeinen muutos
        (click :.kumoa-nappi)
        (<! (paivita))
        (is (= {} @tila) "Kaikki muutokset peruttu")

        (lopeta!)
        (done)))))
