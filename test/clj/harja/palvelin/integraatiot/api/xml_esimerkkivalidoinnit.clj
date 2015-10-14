(ns harja.palvelin.integraatiot.api.xml_esimerkkivalidoinnit
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.api.tyokalut.xml-esimerkit :as xml-esimerkit]
            [clojure.java.io :as io]))



(defn validoi [xsd-polku skeematiedosto esimerkkipolku]
  (xml/validoi xsd-polku skeematiedosto (slurp (io/resource esimerkkipolku))))

(deftest validoi-tierekisteri-xmlsanomat
  (let [xsd-polku "xsd/tierekisteri/schemas/"]
    (is (true? (validoi xsd-polku "haeTietolaji.xsd" xml-esimerkit/+hae-tietolaji-request+)))
    (is (true? (validoi xsd-polku "haeTietue.xsd" xml-esimerkit/+hae-tietue-request+)))
    (is (true? (validoi xsd-polku "haeTietueet.xsd" xml-esimerkit/+hae-tietueet-request+)))
    (is (true? (validoi xsd-polku "lisaaTietue.xsd" xml-esimerkit/+lisaa-tietue-request+)))
    (is (true? (validoi xsd-polku "paivitaTietue.xsd" xml-esimerkit/+paivita-tietue-request+)))
    (is (true? (validoi xsd-polku "poistaTietue.xsd" xml-esimerkit/+poista-tietue-request+)))
    (is (true? (validoi xsd-polku "vastaus.xsd" xml-esimerkit/+hae-tietue-response+)))
    (is (true? (validoi xsd-polku "vastaus.xsd" xml-esimerkit/+hae-tietueet-response+)))
    (is (true? (validoi xsd-polku "vastaus.xsd" xml-esimerkit/+ok-vastaus-response+)))
    (is (true? (validoi xsd-polku "vastaus.xsd" xml-esimerkit/+virhe-tietueen-lisays-epaonnistui-response+)))
    (is (true? (validoi xsd-polku "vastaus.xsd" xml-esimerkit/+virhe-vastaus-tietolajia-ei-loydy-response+)))
    (is (true? (validoi xsd-polku "vastaus.xsd" xml-esimerkit/+virhe-vastaus-tietuetta-ei-loydy-response+)))))

(deftest validoi-tloik-xmlsanomat
  (let [xsd-polku "xsd/tloik/"]
    (is (true? (validoi xsd-polku "ilmoitus.xsd" xml-esimerkit/+ilmoitus+)))
    (is (true? (validoi xsd-polku "ilmoitustoimenpide.xsd" xml-esimerkit/+ilmoitustoimenpide+)))
    (is (true? (validoi xsd-polku "kuittaus.xsd" xml-esimerkit/+valityskuittaus+)))
    (is (true? (validoi xsd-polku "kuittaus.xsd" xml-esimerkit/+vastaanottokuittus+)))))


