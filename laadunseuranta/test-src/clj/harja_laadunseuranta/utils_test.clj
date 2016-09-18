(ns harja-laadunseuranta.utils-test
  (:require [harja-laadunseuranta.utils :refer [select-non-nil-keys]])
  (:use [clojure.test]))

(deftest non-nil-keys
  (is (= {:a 2} (select-non-nil-keys {:a 2 :b nil} [:a :b :c]))))
