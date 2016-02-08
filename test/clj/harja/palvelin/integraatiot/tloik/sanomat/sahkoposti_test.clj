(ns harja.palvelin.integraatiot.tloik.sanomat.sahkoposti-test
  (:require [harja.palvelin.integraatiot.tloik.sanomat.sahkoposti :as s]
            [clojure.test :as t :refer [deftest is use-fixtures]]))

(def +sahkoposti-xsd+ "xsd/sahkoposti/sahkoposti.xsd")
(def +sahkoposti-esimerkki+ "resources/xsd/sahkoposti/esimerkit/sahkoposti.xml")

(deftest viestin-luku
  (let [{:keys [viesti-id lahettaja vastaanottaja otsikko sisalto]}
        (s/lue-sahkoposti (slurp +sahkoposti-esimerkki+))]
    (is (= viesti-id "21EC2020-3AEA-4069-A2DD-08002B30309D"))
    (is (= lahettaja "harja@liikennevirasto.fi"))
    (is (= vastaanottaja "erkki.esimerkki@domain.foo"))
    (is (= otsikko "Testiviesti"))
    (is (= sisalto "Tämä on testiviesti!"))))
