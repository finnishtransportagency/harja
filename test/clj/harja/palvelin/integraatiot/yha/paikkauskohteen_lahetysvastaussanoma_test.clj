(ns harja.palvelin.integraatiot.yha.paikkauskohteen-lahetysvastaussanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.sanomat.paikkauskohteen-lahetysvastaussanoma :as pakkauskohteen-lahetysvastaussanoma])
  (:use [slingshot.slingshot :only [try+]]))

(def virhesanoma (slurp "resources/json/yha/esimerkit/paikkausten-vienti-response.json"))
(def ok-sanoma "[]")

(deftest tarkista-virhesanoma
  (let [vastaus (pakkauskohteen-lahetysvastaussanoma/lue-sanoma virhesanoma)
        virheet (:virheet vastaus)
        virhe (first virheet)]
    (is (false? (:onnistunut vastaus)) "Vastaus tulkittiin onnistuneesti virheeksi.")
    (is (= 3 (count virheet)) "Virheiden määrä täsmää.")
    (is (= 1 (:paikkaus-id virhe)))
    (is (= "Kaista 87 ei ole validi." (:virheviesti virhe)))))

(deftest tarkista-onnistunut-sanoma
  (let [vastaus (pakkauskohteen-lahetysvastaussanoma/lue-sanoma ok-sanoma)]
    (is (true? (:onnistunut vastaus)) "Vastaus tulkittiin oikein onnistuneeksi.")))