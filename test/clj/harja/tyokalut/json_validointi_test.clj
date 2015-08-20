(ns harja.tyokalut.json-validointi-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.json_validointi :as json]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.java.io :as io]
            [slingshot.slingshot :refer [try+]]
            [slingshot.test]
            [cheshire.core :as cheshire]))

(deftest tarkista-json-datan-validius
  (let [json-data (slurp (io/resource "api/examples/virhe-response.json"))]
    (json/validoi skeemat/+virhevastaus+ json-data)))

(deftest tarkista-epavalidi-json-data
  (let [json-data (clojure.string/replace (slurp (io/resource "api/examples/virhe-response.json")) "\"virhe\"" "\"rikki\"")]
    (try+
      (json/validoi skeemat/+virhevastaus+ json-data)
      (assert false "Invalidi JSON ei aiheuttanut oletettua poikkeusta")
      (catch [:type virheet/+invalidi-json+] {:keys [virheet]}
        (println virheet)
        (is (.contains (:viesti (first virheet)) "JSON ei ole validia"))))))

(deftest urakkahaun-vastaus
  (try+
   (json/validoi skeemat/+urakan-haku-vastaus+
                 (slurp (io/resource "api/examples/urakan-haku-response.json")))
   (catch [:type virheet/+invalidi-json+] {:keys [virheet]}
     (println virheet)
     (is (.contains (:viesti (first virheet)) "JSON ei ole validia")))))
