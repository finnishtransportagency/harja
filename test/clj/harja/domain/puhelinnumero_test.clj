(ns harja.domain.puhelinnumero-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [harja.domain.puhelinnumero :as puhelinnumero]))

(deftest tarkista-puhelinnumeron-kanonisointi
  (is (= "+358415442083" (puhelinnumero/kanonisoi "0415442083")) "Lokaali muoto muutettiin kansainväliseksi")
  (is (= "+358415442083" (puhelinnumero/kanonisoi "+358-415442083")) "Väliviivat on pudotettu")
  (is (= "+358415442083" (puhelinnumero/kanonisoi "+358 41 544 20 83")) "Välilyönnit on pudotettu")
  (is (= "+358415442083" (puhelinnumero/kanonisoi "+358 E 41 544 a 20 83")) "Aakkoset on pudotettu")
  (is (nil? (puhelinnumero/kanonisoi nil)) "Tyhjä puhelinnumero käsitellään oikein")
  (is (nil? (puhelinnumero/kanonisoi "")) "Tyhjä puhelinnumero käsitellään oikein"))


