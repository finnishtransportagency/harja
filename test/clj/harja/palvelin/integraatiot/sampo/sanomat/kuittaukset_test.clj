(ns harja.palvelin.integraatiot.sampo.sanomat.kuittaukset_test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sisaan-sanoma :as kuittaus-sisaan-sanoma]))

(deftest lue-onnistunut-kuittaus
  (let [xml (slurp "test/resurssit/sampo/maksuera_ack.xml")]
    (let [vastaus (kuittaus-sisaan-sanoma/lue-kuittaus xml)]
      (is (= "ID:6c321b59:1460814e5:14AE0F721BF" (:viesti-id vastaus))
          "Kuittaus tulkittiin onnistuneeksi ja siltä saatiin luettua oikea viesti id")
      (is (= :kustannussuunnitelma (:viesti-tyyppi vastaus)) "Vastauksen tyyppi pääteltiin oikein"))))

(deftest lue-epaonnistunut-kuittaus
  (let [xml (slurp "test/resurssit/sampo/maksuera_nack.xml")]
    (let [vastaus (kuittaus-sisaan-sanoma/lue-kuittaus xml)]
      (let [virhe (first (:virheet vastaus))
            varoitus (second (:virheet vastaus))]
        (is (= :sampo-raportoi-virheita (:virhe vastaus)) "Virhe ilmoitus on oikea")
        (is (= "FATAL" (:vakavuus virhe)) "Virheen vakavuus on saatu luettua oikein")
        (is (= "OBS unit (Päivittäinen kunnossapito) is invalid. OBS unit path (/Päivittäinen kunnossapito/Hoito) is invalid. So, the OBS Association was not made. OBS association has been updated according to financial information." (:kuvaus virhe)))
        (is (= "WARNING" (:vakavuus varoitus)) "Virheen vakavuus on saatu luettua oikein")
        (is (= "Object: task Attribute: vv_linked_op is required in partition NIKU.ROOT but has no value" (:kuvaus varoitus)) "Virheen vakavuus on saatu luettua oikein")))))