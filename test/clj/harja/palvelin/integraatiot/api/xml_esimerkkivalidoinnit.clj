(ns harja.palvelin.integraatiot.api.xml_esimerkkivalidoinnit
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.api.tyokalut.xml-esimerkit :as xml-esimerkit]
            [clojure.java.io :as io]))

(defn validoi [xsd-polku skeematiedosto esimerkkipolku]
  (xml/validi-xml? xsd-polku skeematiedosto (slurp (io/resource esimerkkipolku))))

(deftest validoi-tloik-xmlsanomat
         (let [xsd-polku "xsd/tloik/"]
              (is (true? (validoi xsd-polku "harja-tloik.xsd" xml-esimerkit/+ilmoitus+)))
              (is (true? (validoi xsd-polku "harja-tloik.xsd" xml-esimerkit/+ilmoitustoimenpide+)))
              (is (true? (validoi xsd-polku "harja-tloik.xsd" xml-esimerkit/+harja-kuittaus+)))
              (is (true? (validoi xsd-polku "harja-tloik.xsd" xml-esimerkit/+vastaanottokuittus+)))
              (is (true? (validoi xsd-polku "harja-tloik.xsd" xml-esimerkit/+ilmoitusperuutus+)))))
