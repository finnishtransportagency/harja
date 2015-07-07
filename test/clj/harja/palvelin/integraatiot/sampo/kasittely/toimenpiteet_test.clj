(ns harja.palvelin.integraatiot.sampo.kasittely.toimenpiteet-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]))

(deftest tarkista-toimenpiteen-tallentuminen
  (tuo-toimenpide)
  (is (= 1 (count (hae-toimenpiteet))) "Luonnin jälkeen toimenpide löytyy Sampo id:llä.")
  (poista-toimenpide))

(deftest tarkista-toimenpiteen-paivittaminen
  (tuo-toimenpide)
  (tuo-toimenpide)
  (is (= 1 (count (hae-toimenpiteet))) "Tuotaessa sama toimenpide uudestaan, päivitetään vanhaa eikä luoda uutta.")
  (poista-toimenpide))

(deftest tarkista-urakan-asettaminen-toimenpiteelle-urakka-ensin
  (tuo-urakka)
  (tuo-toimenpide)
  (is (onko-urakka-sidottu-toimenpiteeseen?) "Toimenpide viittaa oikeaan urakkaan, kun urakka on tuotu ensin.")
  (poista-toimenpide)
  (poista-urakka))

(deftest tarkista-urakan-asettaminen-toimenpiteelle-toimenpide-ensin
  (tuo-toimenpide)
  (tuo-urakka)
  (is (onko-urakka-sidottu-toimenpiteeseen?) "Toimenpide viittaa oikeaan urakkaan, kun toimenpide on tuotu ensin.")
  (poista-toimenpide)
  (poista-urakka))
