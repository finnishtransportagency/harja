(ns harja.palvelin.integraatiot.tievelho.tievelho-test
  (:require [clojure.test :refer :all]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.tievelho.tievelho-komponentti :as tievelho]))

(deftest hae-tievelhosta
         (is (= 5 (+ 2 2)) "Koodia puuttuu vielä"))