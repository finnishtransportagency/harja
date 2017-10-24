(ns harja.palvelin.integraatiot.sampo.kasittely.organisaatiot-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest tarkista-organisaation-tallentuminen
  (tuo-organisaatio)
  (let [organisaatiot (hae-organisaatiot)]
    (is (= 1 (count organisaatiot)) "Luonnin jälkeen organisaatio löytyy Sampo id:llä.")
    (is (every? #(= "urakoitsija" (second %)) organisaatiot) "Jokainen organisaatio on luotu urakoitsijaksi"))
  (poista-organisaatio))

(deftest tarkista-organisaation-paivittaminen
  (tuo-organisaatio)
  (tuo-organisaatio)
  (is (= 1 (count (hae-organisaatiot))) "Tuotaessa sama organisaatio uudestaan, päivitetään vanhaa eikä luoda uutta.")
  (poista-organisaatio))

(deftest tarkista-urakoitsijan-asettaminen-urakalle-sopimus-ensin
  (tuo-urakka)
  (tuo-sopimus)
  (tuo-organisaatio)
  (is onko-urakoitsija-asetettu-urakalle?
      "Organisaatio on merkitty sopimuksen kautta urakalle urakoitsijaksi, kun sopimus on tuotu ensiksi.")
  (poista-organisaatio))

(deftest tarkista-urakoitsijan-asettaminen-urakalle-organisaatio-ensin
  (tuo-organisaatio)
  (tuo-urakka)
  (tuo-sopimus)
  (is onko-urakoitsija-asetettu-urakalle?
      "Organisaatio on merkitty sopimuksen kautta urakalle urakoitsijaksi, kun organisaatio on tuotu ensiksi.")
  (poista-organisaatio))







