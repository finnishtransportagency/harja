(ns harja.palvelin.integraatiot.api.xml_esimerkkivalidoinnit
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.api.tyokalut.xml-skeemat :as xml-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.xml-esimerkit :as xml-esimerkit]
            [clojure.java.io :as io]))

(def +xsd-polku+ "xsd/tierekisteri/schemas/")

(defn validoi [skeematiedosto esimerkkipolku]
  (xml/validoi +xsd-polku+ skeematiedosto (slurp (io/resource esimerkkipolku))))

(deftest validoi-xmlt
  (is (true? (validoi "haeTietolaji.xsd" xml-esimerkit/+hae-tietolaji-request+)))
  (is (true? (validoi "haeTietue.xsd" xml-esimerkit/+hae-tietue-request+)))
  (is (true? (validoi "haeTietueet.xsd" xml-esimerkit/+hae-tietueet-request+)))
  (is (true? (validoi "lisaaTietue.xsd" xml-esimerkit/+lisaa-tietue-request+)))
  (is (true? (validoi "paivitaTietue.xsd" xml-esimerkit/+paivita-tietue-request+)))
  (is (true? (validoi "poistaTietue.xsd" xml-esimerkit/+poista-tietue-request+))))