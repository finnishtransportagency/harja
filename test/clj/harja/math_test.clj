(ns harja.math-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [harja.math :as math]
    [clj-time.core :as t]))

(deftest pisteiden-valiset-etaisyydet-lasketaan-oikein
  (is (== (math/pisteiden-etaisyys [1 1] [2 1]) 1))
  (is (= (format "%.2f" (math/pisteiden-etaisyys [1 1] [2 2])) "1,41"))
  (is (= (format "%.2f" (math/pisteiden-etaisyys [1 10] [15 16])) "15,23")))

