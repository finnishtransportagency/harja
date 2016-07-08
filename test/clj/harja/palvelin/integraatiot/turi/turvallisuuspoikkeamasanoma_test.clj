(ns harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.kyselyt.turvallisuuspoikkeamat :as q]
            [harja.palvelin.integraatiot.turi.turvallisuuspoikkeamasanoma :as sanoma]
            [harja.tyokalut.xml :as xml]
            [harja.kyselyt.konversio :as konv]))

(deftest tarkista-sanoman-muodostus
  (let [data (first (konv/sarakkeet-vektoriin
                (into []
                      q/turvallisuuspoikkeama-xf
                      (q/hae-turvallisuuspoikkeama (luo-testitietokanta) 1))
                {:korjaavatoimenpide :korjaavattoimenpiteet
                 :kommentti :kommentit
                 :liite :liitteet}))
        _ (log/debug "Data on: " (pr-str data))
        xml (sanoma/muodosta data)]
    (is (xml/validoi "xsd/turi/" "poikkeama-rest.xsd" xml)) "Tehty sanoma on XSD-skeeman mukainen"))