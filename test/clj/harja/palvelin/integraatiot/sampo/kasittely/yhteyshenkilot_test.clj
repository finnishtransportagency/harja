(ns harja.palvelin.integraatiot.sampo.kasittely.yhteyshenkilot-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]))

(deftest tarkista-yhteyshenkilon-tallentuminen
  (tuo-yhteyshenkilo)
  (is (= 1 (count (hae-yhteyshenkilot))) "Luonnin jälkeen yhteyshenkilö löytyy Sampo id:llä.")
  (poista-yhteyshenkilo))

(deftest tarkista-yhteyshenkilon-paivittaminen
  (tuo-yhteyshenkilo)
  (tuo-yhteyshenkilo)
  (is (= 1 (count (hae-yhteyshenkilot))) "Tuotaessa sama yhteyshenkilö uudestaan, päivitetään vanhaa eikä luoda uutta.")
  (poista-yhteyshenkilo))

(deftest tarkista-yhteyshenkilon-sitominen-urakkaan-yhteyshenkilo-ensin
  (tuo-yhteyshenkilo)
  (tuo-urakka)
  (is (onko-yhteyshenkilo-asetettu-urakalle?) "Yhteyshenkilö on asetettu urakalle, kun yhteyshenkilö on tuotu ensin.")
  (poista-yhteyshenkilo)
  (poista-urakka))

(deftest tarkista-yhteyshenkilon-sitominen-urakkaan-urakka-ensin
  (tuo-urakka)
  (tuo-yhteyshenkilo)
  (is (onko-yhteyshenkilo-asetettu-urakalle?) "Yhteyshenkilö on asetettu urakalle, kun yhteyshenkilö on tuotu ensin.")
  (poista-yhteyshenkilo)
  (poista-urakka))