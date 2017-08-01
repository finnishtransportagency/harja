(ns harja.palvelin.palvelut.yllapitokohteet.viestinta-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.palvelut.yllapitokohteet.viestinta :as viestinta]))

(deftest ilmoittaja-formatoidaan-oikein
  (is (= (viestinta/formatoi-ilmoittaja {:etunimi "Uki" :sukunimi "Päällystysmies"})
         "Uki Päällystysmies"))

  (is (= (viestinta/formatoi-ilmoittaja {:etunimi "Uki" :sukunimi "Päällystysmies" :puhelin "040"})
         "Uki Päällystysmies (puh. 040)"))

  (is (= (viestinta/formatoi-ilmoittaja {:etunimi "Uki" :sukunimi "Päällystysmies" :puhelin "040"
                                         :organisaatio {:nimi "Ukin oma tienpäällystysfirma OY"}})
         "Uki Päällystysmies (puh. 040) (org. Ukin oma tienpäällystysfirma OY)"))

  (is (= (viestinta/formatoi-ilmoittaja {:etunimi "Uki" :sukunimi "Päällystysmies" :puhelin nil
                                         :organisaatio {:nimi "Ukin oma tienpäällystysfirma OY"}})
         "Uki Päällystysmies (org. Ukin oma tienpäällystysfirma OY)"))

  (is (= (viestinta/formatoi-ilmoittaja {:puhelin nil
                                         :organisaatio {:nimi "Ukin oma tienpäällystysfirma OY"}})
         "Ukin oma tienpäällystysfirma OY"))

  (is (= (viestinta/formatoi-ilmoittaja {:puhelin 040
                                         :organisaatio {:nimi "Ukin oma tienpäällystysfirma OY"}})
         "Ukin oma tienpäällystysfirma OY")))