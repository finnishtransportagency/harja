(ns harja.palvelin.integraatiot.sampo.kasittely.hankkeet-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]
            [harja.palvelin.integraatiot.sampo.kasittely.hankkeet :as hankkeet]))

(use-fixtures :once tietokanta-fixture)

(deftest tarkista-hankkeen-tallentuminen
  (tuo-hanke)
  (is (= 1 (count (hae-hankkeet))) "Luonnin jälkeen hanke löytyy Sampo id:llä.")
  (poista-hanke))

(deftest tarkista-hankkeen-paivittaminen
  (tuo-hanke)
  (tuo-hanke)
  (is (= 1 (count (hae-hankkeet))) "Tuotaessa sama hanke uudestaan, päivitetään vanhaa eikä luoda uutta.")
  (poista-hanke))

(deftest tarkista-alueurakkanumeron-purku
  (let [osat (hankkeet/pura-alueurakkanro "TYS-0666")]
    (is (= "TYS" (:tyypit osat)) "Tyypit on purettu oikein")
    (is (= "0666" (:alueurakkanro osat)) "Alueurakkanumero on purettu oikein"))
  (let [osat (hankkeet/pura-alueurakkanro "TYS0666")]
    (is (nil? (:tyypit osat)) "Tyyppiä ei ole päätelty")
    (is (= "TYS0666" (:alueurakkanro osat)) "Alueurakkanumero on purettu oikein")))
