(ns ^:figwheel-always harja-laadunseuranta.all-tests
  (:require [harja-laadunseuranta.projektiot-test]
            [harja-laadunseuranta.paikannus-test]
            [harja-laadunseuranta.ilmoitukset-test]
            [harja-laadunseuranta.kartta-test]
            [harja-laadunseuranta.reitintallennus-test]
            [harja-laadunseuranta.kaynnistyspainike-test]
            [harja-laadunseuranta.core-test]
            [harja-laadunseuranta.kamera-test]
            [harja-laadunseuranta.utils-test]
            [harja-laadunseuranta.ylapalkki-test]
            [harja-laadunseuranta.kitkamittaus-test]
            [harja-laadunseuranta.sovellus-test]
            [cljs-react-test.utils]
            [cljs.test :as t :refer-macros [run-all-tests]]))

(def phantomjs-test-completion-callback (cljs.core/atom nil))

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (if @phantomjs-test-completion-callback
    (@phantomjs-test-completion-callback (clj->js m))
    (if (cljs.test/successful? m)
      (println "SUCCESS" (pr-str m))
      (println "FAIL" (pr-str m)))))

(defn ^:export run [callback]
  (when callback
    (reset! phantomjs-test-completion-callback callback))
  (run-all-tests #"harja-laadunseuranta.*-test"))

(run nil)

deve