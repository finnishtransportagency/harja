(ns harja.palvelin.integraatiot.sampo.kasittely.sopimukset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        ))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(defn onko-alisopimus-liitetty-paasopimukseen? []
  (first (first (q "SELECT exists(SELECT id
              FROM sopimus
              WHERE paasopimus = (SELECT id
                                  FROM sopimus
                                  WHERE sampoid = 'TESTISOPIMUS'))"))))

(defn onko-sopimus-sidottu-urakkaan? []
  (first (first (q "SELECT exists(SELECT id
              FROM sopimus
              WHERE urakka = (SELECT id
                                  FROM urakka
                                  WHERE sampoid = 'TESTIURAKKA'))"))))

(defn hae-sopimukset []
  (q "select id from sopimus where sampoid = 'TESTISOPIMUS';"))

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

(deftest tarkista-urakan-sitominen-sopimukseen
  (tuo-sopimus)
  (tuo-urakka)
  (is (onko-sopimus-sidottu-urakkaan?) "Sopimus on sidottu urakkaan, kun sopimus on tuotu ensin Samposta.")

  (tuo-urakka)
  (tuo-sopimus)
  (is (onko-sopimus-sidottu-urakkaan?) "Sopimus on sidottu urakkaan, kun urakka on tuotu ensin Samposta.")

  (poista-sopimus)
  (poista-urakka))


