(ns ^:figwheel-always harja.asiakas.test-runner
    "Juoksuttaa testit aina."
  (:require
   [harja.ui.viesti :as viesti]
   [harja.loki :refer [log]]
   ;; require kaikki testit
   [cljs.test :as test]
   [harja.app-test]))


(defmethod test/report [:harja :fail] [event]
  (.log js/console "FAIL: " (pr-str event))
  (viesti/nayta! [:div.testfail
                  [:h3 "Testi epäonnistui:"]
                  [:div.expected "Odotettu: " (pr-str (:expected event))]
                  [:div.actual "Saatu: " (pr-str (:actual event))]
                  (when-let [m (:message event)]
                    [:div.testmessage "Viesti: " m])]
                 :danger))


(defn ^:export aja-testit []
  (test/run-tests (merge (test/empty-env)
                         {:reporter :harja})
                  'harja.app-test))