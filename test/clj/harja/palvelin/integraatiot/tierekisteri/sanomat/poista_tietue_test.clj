(ns harja.palvelin.integraatiot.tierekisteri.sanomat.poista-tietue-test
  (:require [taoensso.timbre :as log]
            [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-poistokutsu :refer :all]
            [harja.tyokalut.xml :as xml]))

(def poistettava-testitietue {:poistaja          {:henkilo      "Keijo Käsittelijä"
                                                  :jarjestelma  "FastMekka"
                                                  :organisaatio "Asfaltia Oy"
                                                  :yTunnus      "1234567-8"}
                              :tunniste          "HAR123456789"
                              :tietolajitunniste "tl506"
                              :poistettu         "2015-05-26+03:00"})

(def +xsd+ "xsd/tierekisteri/schemas/")

(deftest tarkista-kutsu
  (let [kutsu-xml (muodosta-kutsu poistettava-testitietue)
        xsd "poistaTietue.xsd"]
    (is (xml/validoi +xsd+ xsd kutsu-xml) "Muodostettu kutsu on XSD-skeeman mukainen")))
