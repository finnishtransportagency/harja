(ns harja.palvelin.integraatiot.sampo.kasittely.urakat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]
            [harja.palvelin.integraatiot.sampo.kasittely.sopimukset-test :as sopimus-testi]))

(defn hae-urakat []
  (q "select id from urakka where sampoid = 'TESTIURAKKA';"))

(deftest tarkista-urakan-tallentuminen
  (tuo-urakka)
  (is (= 1 (count (hae-urakat))) "Luonnin jälkeen urakka löytyy Sampo id:llä.")

  (is (= 1 (count (q "SELECT id FROM yhteyshenkilo_urakka
                      WHERE rooli = 'Sampo yhteyshenkilö' AND
                            urakka = (SELECT id FROM urakka
                            WHERE sampoid = 'TESTIURAKKA');")))
      "Urakalle löytyy luonnin jälkeen sampoid:llä sidottu yhteyshenkilö.")

  (poista-urakka))

(deftest tarkista-urakan-paivittaminen
  (tuo-urakka)
  (tuo-urakka)
  (is (= 1 (count (hae-urakat))) "Tuotaessa sama urakka uudestaan, päivitetään vanhaa eikä luoda uutta.")
  (poista-urakka))

(deftest tarkista-urakoitsijan-sitominen-urakkaan
  (tuo-sopimus)
  (tuo-urakka))



