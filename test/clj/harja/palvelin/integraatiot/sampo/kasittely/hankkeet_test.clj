(ns harja.palvelin.integraatiot.sampo.kasittely.hankkeet-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]))


(deftest tarkista-hankkeen-tallentuminen
  (tuo-hanke)
  (is (= 1 (count (q "select id from hanke where sampoid = 'TESTIHANKE';")))
      "Luonnin jälkeen hanke löytyy Sampo id:llä.")
  (poista-hanke))

(deftest tarkista-hankkeen-paivittaminen
  (tuo-hanke)
  (tuo-hanke)
  (is (= 1 (count (q "select id from hanke where sampoid = 'TESTIHANKE';")))
      "Tuotaessa sama hanke uudestaan, päivitetään vanhaa eikä luoda uutta.")
  (poista-hanke))
