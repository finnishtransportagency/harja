(ns harja.tyokalut.json-validointi-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.json_validointi :as json]
            [harja.palvelin.api.skeemat :as skeemat]
            [clojure.java.io :as io]))



(deftest tarkista-json-datan-validius
  (let [json-data (slurp (io/resource "api/examples/virhe-response.json"))]
    (is (json/validoi skeemat/+virhevastaus+ json-data))))