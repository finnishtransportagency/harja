(ns harja.tyokalut.yleiset-test
  "Yleisten työkalujen testit"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.yleiset :as yleiset-tyokalut]))

(deftest round2-test
  (testing "round2 pyöristää oikein"
    (is (= 3.14 (yleiset-tyokalut/round2 2 3.14159)))
    (is (= 2.72 (yleiset-tyokalut/round2 2 2.71828)))
    (is (= 0.00 (yleiset-tyokalut/round2 2 0.0049)))
    (is (= 0.1 (yleiset-tyokalut/round2 1 0.0549)))
    (is (= 0.01 (yleiset-tyokalut/round2 2 0.0051)))
    (is (= 123457.0 (yleiset-tyokalut/round2 0 123456.789)))
    (is (= -1.23 (yleiset-tyokalut/round2 2 -1.2345)))))
