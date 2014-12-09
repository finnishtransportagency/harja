(ns selaintestit.sivu-latautuu
  (:require [clj-webdriver.taxi :refer :all :as s]
            [clojure.test :refer :all]
            [selaintestit.selain :refer [browser-up browser-down]]))


(deftest laskeutumissivun-lataus
  (browser-up)
  (to "http://localhost:3000")
  (wait-until #(execute-script "return window['HARJA_LADATTU'] === true") 15000 1000)
  (browser-down))

