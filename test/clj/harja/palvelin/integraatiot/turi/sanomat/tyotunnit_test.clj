(ns harja.palvelin.integraatiot.turi.sanomat.tyotunnit-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.turi.sanomat.tyotunnit :as sanoma]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]))

(deftest tyotuntisanoman-muodostus
  (let [sampoid "1242141-OULU2"
        urakka {:tilaajanvastuuhenkilo-sukunimi "Penttikoski",
                :urakka-ely nil,
                :alueurakkanro "1238",
                :tilaajanvastuuhenkilo-kayttajatunnus "LXPENTTIER",
                :urakka-loppupvm #inst"2019-09-29T21:00:00.000-00:00",
                :hanke-sampoid "oulu2",
                :tilaajanvastuuhenkilo-etunimi "Erno",
                :hanke-nimi "Oulun alueurakka",
                :tilaajanvastuuhenkilo-sposti "erno@penttikoski.com",
                :urakka-tyyppi "hoito",
                :urakka-nimi "Oulun alueurakka 2014-2019",
                :urakka-sampoid sampoid}
        vuosi 1996
        kolmannes 3
        tunnit 666
        xml (sanoma/muodosta urakka vuosi kolmannes tunnit)
        data (xml/lue xml)]
    (is (xml/validi-xml? "xsd/turi/" "tyotunnit-rest.xsd" xml)) "Tuotettu XML on validia"
    (is (= "Harja" (z/xml1-> data :lahdejarjestelma z/text)) "Lähdejärjestelmä on odotettu")
    (is (= sampoid (z/xml1-> data :sampourakkaid z/text)) "Urakan Sampo id on odotettu")
    (is (= (str vuosi) (z/xml1-> data :vuosi z/text)) "Vuosi on odotettu")
    (is (= (str kolmannes) (z/xml1-> data :vuosikolmannes z/text)) "Vuosikolmannes on odotettu")
    (is (= (str tunnit) (z/xml1-> data :tyotunnit z/text)) "Vuosikolmannes on odotettu")))