(ns harja.palvelin.integraatiot.sampo.kasittely.hankkeet-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]))

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
