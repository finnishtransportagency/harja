(ns harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma :as sanoma]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]))

(deftest tarkista-sanoman-muodostus
  (let [turpo-idt (flatten (q "SELECT id FROM turvallisuuspoikkeama"))]
    (log/debug "Validoidaan turpo-idt: " (pr-str turpo-idt))
    (doseq [id turpo-idt]
      (log/debug "Validoidaan id:" id)
      (let [data (turi/hae-turvallisuuspoikkeama (luo-liitteidenhallinta) (luo-testitietokanta) id)
           xml (sanoma/muodosta data)]
       (is (xml/validoi "xsd/turi/" "poikkeama-rest.xsd" xml)) "Tehty sanoma on XSD-skeeman mukainen"))))