(ns harja.tyokalut.spec-apurit-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [harja.testi :refer :all]
    [harja.tyokalut.spec-apurit :as spec-apurit]))

(deftest nil-arvojen-poisto-mapista-toimii
  (is (= (spec-apurit/poista-nil-avaimet {:a "1" :b nil}) {:a "1"}))
  (is (= (spec-apurit/poista-nil-avaimet {:a "1" :b "2"}) {:a "1" :b "2"}))
  (is (= (spec-apurit/poista-nil-avaimet {:a "1" :b {:c "3"}}) {:a "1" :b {:c "3"}}))
  (is (= (spec-apurit/poista-nil-avaimet {:a "1" :b {:c nil}}) {:a "1"}))
  (is (= (spec-apurit/poista-nil-avaimet {:a nil :b {:c "3"}}) {:b {:c "3"}})))

