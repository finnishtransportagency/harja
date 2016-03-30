(ns harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.kyselyt.turvallisuuspoikkeamat :as q]
            [harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma :as sanoma]
            [harja.tyokalut.xml :as xml]))

(deftest tarkista-sanoman-muodostus
  (let [data (first (q/hae-turvallisuuspoikkeama (luo-testitietokanta) 1))
        xml (sanoma/muodosta data)]
    (is (not(xml/validoi "xsd/turi/" "turvallisuuspoikkeama.xsd" xml)) "Tehty sanoma on XSD-skeeman mukainen")))

