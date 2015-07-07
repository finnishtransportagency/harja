(ns harja.palvelin.integraatiot.sampo.kasittely.sopimukset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]))

(deftest tarkista-sopimuksen-tallentuminen
  (tuo-sopimus)
  (is (= 1 (count (hae-sopimukset))) "Luonnin jälkeen sopimus löytyy Sampo id:llä.")
  (poista-sopimus))

(deftest tarkista-sopimuksen-paivittaminen
  (tuo-sopimus)
  (tuo-sopimus)
  (is (= 1 (count (hae-sopimukset))) "Tuotaessa sama sopimus uudestaan, päivitetään vanhaa eikä luoda uutta.")
  (poista-sopimus))

(deftest tarkista-paasopimuksen-ja-alisopimuksen-tallentaminen
  (tuo-sopimus)
  (tuo-alisopimus)
  (is (onko-alisopimus-liitetty-paasopimukseen?) "Ensimmäisenä luotu sopimus tehdään pääsopimuksessa, jolle seuraavat sopimukset ovat alisteisia.")
  (poista-alisopimus)
  (poista-sopimus))

(deftest tarkista-urakan-sitominen-sopimukseen-sopimus-ensin
  (tuo-sopimus)
  (tuo-urakka)
  (is (onko-sopimus-sidottu-urakkaan?) "Sopimus on sidottu urakkaan, kun sopimus on tuotu ensin Samposta.")
  (poista-sopimus)
  (poista-urakka))

(deftest tarkista-urakan-sitominen-sopimukseen-urakka-ensin
  (tuo-urakka)
  (tuo-sopimus)
  (is (onko-sopimus-sidottu-urakkaan?) "Sopimus on sidottu urakkaan, kun urakka on tuotu ensin Samposta.")
  (poista-sopimus)
  (poista-urakka))