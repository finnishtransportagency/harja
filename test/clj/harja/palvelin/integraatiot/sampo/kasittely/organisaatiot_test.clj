(ns harja.palvelin.integraatiot.sampo.kasittely.organisaatiot-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]))

(defn hae-organisaatiot []
  (q "select id from organisaatio where sampoid = 'TESTIORGANISAATI';"))

(deftest tarkista-organisaation-tallentuminen
  (tuo-organisaatio)
  (is (= 1 (count (hae-organisaatiot))) "Luonnin jälkeen organisaatio löytyy Sampo id:llä.")
  (poista-organisaatio))

(deftest tarkista-organisaation-paivittaminen
  (tuo-organisaatio)
  (tuo-organisaatio)
  (is (= 1 (count (hae-organisaatiot))) "Tuotaessa sama organisaatio uudestaan, päivitetään vanhaa eikä luoda uutta.")
  (poista-organisaatio))




