(ns harja.palvelin.integraatiot.yha.kohteen-lahetysvastaussanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetysvastaussanoma :as kohteen-lahetysvastaussanoma])
  (:use [slingshot.slingshot :only [try+]]))

(def virhesanoma "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<urakan-kohteiden-toteumatietojen-kirjausvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">\n    <!--Optional:-->\n    <virheet>\n        <virhe>\n            <kohde-yha-id>1</kohde-yha-id>\n            <selite>Jotain meni vikaan</selite>\n        </virhe>\n    </virheet>\n</urakan-kohteiden-toteumatietojen-kirjausvastaus>")

(def onnistunutsanoma "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<urakan-kohteiden-toteumatietojen-kirjausvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">\n</urakan-kohteiden-toteumatietojen-kirjausvastaus>")

(deftest tarkista-virhesanoma
  (let [vastaus (kohteen-lahetysvastaussanoma/lue-sanoma virhesanoma)
        virheet (:virheet vastaus)
        virhe (first virheet)]
    (is (false? (:onnistunut vastaus)) "Vastaus tulkittiin onnistuneesti virheeksi")
    (is (= 1 (count virheet)))
    (is (= 1 (:kohde-yha-id virhe)))
    (is (= "Jotain meni vikaan" (:selite virhe)))))

(deftest tarkista-onnistunut-sanoma
  (let [vastaus (kohteen-lahetysvastaussanoma/lue-sanoma onnistunutsanoma)]
    (is (true? (:onnistunut vastaus)) "Vastaus tulkittiin onnistuneesti virheeksi")))
