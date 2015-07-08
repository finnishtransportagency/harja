(ns harja.palvelin.integraatiot.sampo.kasittely.urakat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]))

(deftest tarkista-urakan-tallentuminen
  (tuo-urakka)
  (is (= 1 (count (hae-urakat))) "Luonnin jälkeen urakka löytyy Sampo id:llä.")
  (poista-urakka))

(deftest tarkista-urakan-paivittaminen
  (tuo-urakka)
  (tuo-urakka)
  (is (= 1 (count (hae-urakat))) "Tuotaessa sama urakka uudestaan, päivitetään vanhaa eikä luoda uutta.")
  (poista-urakka))

(deftest tarkista-yhteyshenkilon-sitominen-urakkaan
  (tuo-urakka)
  (is (onko-yhteyshenkilo-sidottu-urakkaan?) "Urakalle löytyy luonnin jälkeen sampoid:llä sidottu yhteyshenkilö.")
  (poista-urakka))

(deftest tarkista-urakkatyypin-asettaminen
  (tuo-urakka)
  (is (= "hoito" (hae-urakan-tyyppi)) "Urakkatyyppi on asetettu oikein ennen kuin hanke on tuotu.")
  (poista-urakka)

  (tuo-urakka)
  (tuo-hanke)
  (is (= "paallystys" (hae-urakan-tyyppi)) "Urakkatyyppi on asetettu oikein kun urakka on tuotu ensin.")
  (poista-urakka)
  (poista-hanke)

  (tuo-hanke)
  (tuo-urakka)
  (is (= "paallystys" (hae-urakan-tyyppi)) "Urakkatyyppi on asetettu oikein kun hanke on tuotu ensin.")
  (poista-urakka)
  (poista-hanke))