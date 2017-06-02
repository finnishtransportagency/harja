(ns harja.domain.tierekisteri.tietolajit-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [harja.domain.tierekisteri.tietolajit :as tietolajit]))

(deftest tarkista-arvoalueen-validointi
  (is (nil? (tietolajit/validoi-arvoalue "1" "tl666" "kenttänen" :numeerinen 1 10))
      "Arvoalueen sisällä oleva arvo ei aiheuta poikkeusta")
  (is (thrown-with-msg?
        Exception
        #"Kentän arvon: kenttänen pitää olla vähemmän kuin: 10"
        (tietolajit/validoi-arvoalue "11" "tl666" "kenttänen" :numeerinen 1 10)))
  (is (thrown-with-msg?
        Exception
        #"Kentän arvon: kenttänen pitää olla vähintään: 1"
        (tietolajit/validoi-arvoalue "-1" "tl666" "kenttänen" :numeerinen 1 10)))
  (is (nil? (tietolajit/validoi-arvoalue "foo" "tl666" "kenttänen" :merkkijono 1 10))
      "Tekstikenttiä ei validoida")
  (is (nil? (tietolajit/validoi-arvoalue "" "tl666" "kenttänen" :numeerinen 1 10))
      "Tyhjää arvoa ei validoida")
  (is (nil? (tietolajit/validoi-arvoalue nil "tl666" "kenttänen" :numeerinen 1 10))
      "Nilliä arvoa ei validoida")
  (is (nil? (tietolajit/validoi-arvoalue "2" "tl666" "kenttänen" :numeerinen nil nil))
      "Validointia ei tehdä, jos arvoaluetta ei ole")
  (is (nil? (tietolajit/validoi-arvoalue "123456789012" "tl666" "kenttänen" :numeerinen 1 123456789015))
      "Isot numerot osataan käsitellä")
  (is (nil? (tietolajit/validoi-arvoalue "1.4" "tl666" "kenttänen" :numeerinen 1 123456789015))
      "Desimaalit osataan käsitellä"))


