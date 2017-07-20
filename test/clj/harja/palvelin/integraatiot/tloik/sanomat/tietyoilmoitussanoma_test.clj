(ns harja.palvelin.integraatiot.tloik.sanomat.ilmoitustoimenpide-sanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tloik.sanomat.tietyoilmoitussanoma :as tietyoilmoitussanoma]
            [harja.palvelin.integraatiot.tloik.tietyoilmoitukset :as tietyoilmoitukset])
  (:import (java.text SimpleDateFormat)
           (java.util UUID)))

(def +xsd-polku+ "xsd/tloik/")

(defn parsi-paivamaara [teksti]
  (.parse (SimpleDateFormat. "dd.MM.yyyy") teksti))

(deftest tarkista-sanoman-validius
  (let [xml (html (tietyoilmoitussanoma/muodosta (tietyoilmoitukset/hae ds 1) (str (UUID/randomUUID))))
        xsd "harja-tloik.xsd"]
    (is (xml/validi-xml? +xsd-polku+ xsd xml) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))