(ns ^:figwheel-always harja.asiakas.test-runner
    "Juoksuttaa testit aina."
  (:require
   [harja.ui.viesti :as viesti]
   [harja.loki :refer [log]]
   ;; require kaikki testit
   [cljs.test :as test]
   [cemerick.cljs.test :as ctest]
   [harja.e2e.test-test]
   [harja.app-test]
   [harja.tiedot.muokkauslukko-test]
   [harja.tiedot.urakka.suunnittelu-test]
   [harja.views.urakka.siltatarkastukset-test]
   [harja.views.urakka.kohdeluettelo.paallystysilmoitukset-test]
   [harja.views.urakka.kohdeluettelo.paikkausilmoitukset-test]
   [harja.pvm-test]))


(def +virheviestin-nayttoaika+ 5000)

(defmethod test/report [:harja :begin-test-ns] [event]
  (.log js/console "Testataan: " (:ns event))) 

(defmethod test/report [:harja :begin-test-var] [event]
  (.log js/console "TEST: " (test/testing-vars-str (:var event))))

(defmethod test/report [:harja :fail] [event]
  (.log js/console "FAIL: " (pr-str event))
  (viesti/nayta! [:div.testfail
                  [:h3 "Testi epäonnistui:"]
                  [:div.expected "Odotettu: " (pr-str (:expected event))]
                  [:div.actual "Saatu: " (pr-str (:actual event))]
                  (when-let [m (:message event)]
                    [:div.testmessage "Viesti: " m])]
                 :danger
                 +virheviestin-nayttoaika+))

(defmethod test/report [:harja :error] [event]
  (.log js/console "ERROR:" (pr-str event))
  (viesti/nayta! [:div.testfail
                  [:h3 "Virhe testin suorituksessa"
                   [:div.expected "Odotettu: " (pr-str (:expected event))]
                   [:div.actual "Saatu: " (pr-str (:actual event))]
                   (when-let [m (:message event)]
                     [:div.testmessage "Viesti: " m])]]
                 :danger
                 +virheviestin-nayttoaika+))

(defmethod test/report [:harja :summary] [event]
  (.log js/console "Testejä ajettu: " (:test event)))

(defn aja-testit []
  (test/run-tests (merge (test/empty-env)
                         {:reporter :harja})
                  'harja.app-test  
                  'harja.tiedot.urakka.suunnittelu-test
                  'harja.tiedot.muokkauslukko-test
                  'harja.views.urakka.siltatarkastukset-test
                  'harja.views.urakka.kohdeluettelo.paallystysilmoitukset-test
                  'harja.views.urakka.kohdeluettelo.paikkausilmoitukset-test
                  'harja.pvm-test)) 

(defmethod ctest/report [:harja-e2e :summary] [event]
  (.log js/console "E2E-testejä ajettu: " (:test event)))

(defmethod ctest/report [:harja-e2e :fail] [event]
  (.log js/console "E2E-FAIL: expected" (pr-str (:expected event)) " actual " (pr-str (:actual event)) (:message event)))

(defmethod ctest/report [:harja-e2e :error] [event]
  (.log js/console "E2E-ERROR: expected " (pr-str (:expected event)) " actual " (pr-str (:actual event)) (:message event)))

(defmethod ctest/report [:harja-e2e :begin-test-ns] [event]
  (.log js/console "E2E-testit " (pr-str (:ns event))))

(defmethod ctest/report [:harja-e2e :begin-test-var] [event]
  (.log js/console "E2E TEST: " (test/testing-vars-str (:var event))))

(defn aja-e2e-testit []
  (ctest/run-tests (merge (test/empty-env)
                          {:reporter :harja-e2e})
                   'harja.e2e.test-test)) 
 
