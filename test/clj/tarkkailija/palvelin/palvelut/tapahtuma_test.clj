(ns tarkkailija.palvelin.palvelut.tapahtuma-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [tarkkailija.palvelin.palvelut.tapahtuma :as tapahtuma]))

(defn tarkkailija-jarjestelma [testit]
  (pystyta-harja-tarkkailija)
  (testit)
  (lopeta-harja-tarkkailija))

(use-fixtures :each tarkkailija-jarjestelma)

(deftest tapahtuma-julkaisija-test
  )
(deftest tapahtuma-datan-spec-test
  )
(deftest lopeta-tapahtuman-kuuntelu-test
  )
(deftest tapahtuman-julkaisia!-test
  )
(deftest yhta-aikaa-tapahtuman-julkaisia!-test
  )
(deftest tapahtuman-kuuntelija!-test
  )
(deftest julkaise-tapahtuma-test
  )
(deftest tarkkaile-tapahtumaa-test
  )
(deftest tarkkaile-tapahtumia-test
  )
