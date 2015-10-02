(ns harja.palvelin.integraatiot.api.xml_esimerkkivalidoinnit
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.api.tyokalut.xml-skeemat :as xml-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.xml-esimerkit :as xml-esimerkit]
            [clojure.java.io :as io]))

(defn validoi [skeemapolku skeematiedosto esimerkkipolku]
  (xml/validoi skeemapolku skeematiedosto (slurp (io/resource esimerkkipolku))))

(deftest validoi-xmlt
  (is (nil? (validoi xml-skeemat/+haeTietolaji+ "haeTietolaji.xsd" xml-esimerkit/+hae-tietolaji-request+))))