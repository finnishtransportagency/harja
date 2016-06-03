(ns harja-laadunseuranta.utils-test
  (:require [harja-laadunseuranta.utils :refer [ryhmat select-non-nil-keys]])
  (:use [clojure.test]))

(deftest ryhmien-parsinta
  (is (= #{} (ryhmat {:headers nil})))
  (is (= #{} (ryhmat {:headers {}})))
  (is (= #{} (ryhmat {:headers {"oam_groups" ""}})))
  (is (= #{"jarjestelmanvalvoja" "paakayttaja"} (ryhmat {:headers {"oam_groups" "Jarjestelmanvalvoja,Paakayttaja"}}))))

(deftest non-nil-keys
  (is (= {:a 2} (select-non-nil-keys {:a 2 :b nil} [:a :b :c]))))
