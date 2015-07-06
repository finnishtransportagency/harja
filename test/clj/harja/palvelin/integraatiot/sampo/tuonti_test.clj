(ns harja.palvelin.integraatiot.sampo.tuonti-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.sampo.tuonti :as tuonti]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]))


(deftest tarkista-hankkeen-tallentuminen
  (tuo-hanke)
  (is (= 1 (count (q "select id from hanke where sampoid = 'TESTIHANKE';")))
      "Luonnin jälkeen hanke löytyy Sampo id:llä.")

  (tuo-hanke)
  (is (= 1 (count (q "select id from hanke where sampoid = 'TESTIHANKE';")))
      "Tuotaessa sama hanke uudestaan, päivitetään vanhaa eikä luoda uutta.")

  (poista-hanke))

(deftest tarkista-urakan-tallentuminen
  (tuo-urakka)
  (is (= 1 (count (q "select id from urakka where sampoid = 'TESTIURAKKA';")))
      "Luonnin jälkeen urakka löytyy Sampo id:llä.")

  (tuo-urakka)
  (is (= 1 (count (q "select id from urakka where sampoid = 'TESTIURAKKA';")))
      "Tuotaessa sama urakka uudestaan, päivitetään vanhaa eikä luoda uutta.")

  (is (= 1 (count (q "SELECT id FROM yhteyshenkilo_urakka
                      WHERE rooli = 'Sampo yhteyshenkilö' AND
                            urakka = (SELECT id FROM urakka
                            WHERE sampoid = 'TESTIURAKKA');")))
      "Urakalle löytyy luonnin jälkeen sampoid:llä sidottu yhteyshenkilö.")

  (poista-urakka))

(deftest tarkista-sopimuksen-tallentuminen
  (tuo-sopimus)
  (is (= 1 (count (q "select id from sopimus where sampoid = 'TESTISOPIMUS';")))
      "Luonnin jälkeen sopimus löytyy Sampo id:llä.")

  (tuo-sopimus)
  (is (= 1 (count (q "select id from sopimus where sampoid = 'TESTISOPIMUS';")))
      "Tuotaessa sama sopimus uudestaan, päivitetään vanhaa eikä luoda uutta.")

  (laheta-viesti-kasiteltavaksi (clojure.string/replace +testisopimus-sanoma+ "TESTISOPIMUS" "TESTIALISOPIMUS"))
  (is (first (first (q "SELECT exists(SELECT id
              FROM sopimus
              WHERE paasopimus = (SELECT id
                                  FROM sopimus
                                  WHERE sampoid = 'TESTISOPIMUS'))")))
      "Ensimmäisenä luotu sopimus tehdään pääsopimuksessa, jolle seuraavat sopimukset ovat alisteisia.")
  (u "delete from sopimus where sampoid = 'TESTIALISOPIMUS'")

  (poista-sopimus))

