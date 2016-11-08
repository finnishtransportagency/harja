(ns harja.palvelin.integraatiot.tloik.sanomat.harjakuittaussanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tloik.sanomat.harja-kuittaus-sanoma :as harja-kuittaus-sanoma]
            [harja.pvm :as pvm])
  (:import (java.io ByteArrayInputStream)
           (java.text SimpleDateFormat)
           (java.util UUID)))

(def +xsd-polku+ "xsd/tloik/")

(defn parsi-paivamaara [teksti]
  (.parse (SimpleDateFormat. "dd.MM.yyyy") teksti))

(deftest tarkista-sanoman-validius
  (let [xml (html (harja-kuittaus-sanoma/muodosta
                    (str (UUID/randomUUID))
                    123456789
                    "2016-02-11T08:59:30.035Z"
                    "valitetty"
                    {:loppupvm "2016-02-11T08:59:30.035Z"
                     :urakoitsija_nimi "YIT Rakennus Oy"
                     :alueurakkanumero 1238
                     :takuu_loppupvm nil
                     :nimi "Oulun alueurakka 2014-2019"
                     :indeksi "MAKU 2005"
                     :id 4
                     :urakoitsija_ytunnus "1565583-5"
                     :alkupvm "2016-02-11T08:59:30.035Z"
                     :tyyppi "hoito"}
                    [{:etunimi "Pentti"
                      :sukunimi "Päivystäjä"
                      :matkapuhelin "0987654346789"
                      :sahkoposti "pentti@example.com"}]
                    nil))
        xsd "harja-tloik.xsd"]
    (is (xml/validi-xml? +xsd-polku+ xsd xml) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))

