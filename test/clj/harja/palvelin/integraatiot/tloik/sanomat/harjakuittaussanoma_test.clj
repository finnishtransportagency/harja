(ns harja.palvelin.integraatiot.tloik.sanomat.harjakuittaussanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tloik.sanomat.harja-kuittaus-sanoma :as harja-kuittaus-sanoma]
            [harja.palvelin.integraatiot.tloik.sanomat.tloik-kuittaus-sanoma :as tloik-kuittaus-sanoma]
            [harja.pvm :as pvm])
  (:import (java.io ByteArrayInputStream)
           (java.text SimpleDateFormat)
           (java.util UUID)))

(def +xsd-polku+ "xsd/tloik/")

(defn parsi-paivamaara [teksti]
  (.parse (SimpleDateFormat. "dd.MM.yyyy") teksti))

(defn urakan-tiedot [nimi]
  {:loppupvm "2016-02-11T08:59:30.035Z"
             :urakoitsija_nimi "YIT Rakennus Oy"
             :alueurakkanumero 1238
             :takuu_loppupvm nil
             :nimi nimi
             :indeksi "MAKU 2005"
             :id 4
             :urakoitsija_ytunnus "1565583-5"
             :alkupvm "2016-02-11T08:59:30.035Z"
             :tyyppi "hoito"})

(defn paivystajat [sukunimi]
  [{:etunimi "Pentti"
    :sukunimi sukunimi
    :matkapuhelin "0987654346789"
    :sahkoposti "pentti@example.com"}])
(deftest tarkista-sanoman-validius
  (let [xml (html (harja-kuittaus-sanoma/muodosta
                    (str (UUID/randomUUID))
                    123456789
                    "2016-02-11T08:59:30.035Z"
                    "valitetty"
                    (urakan-tiedot "Oulun alueurakka 2014-2019")
                    (paivystajat "Päivystäjä")
                    nil))
        xsd "harja-tloik.xsd"]
    (is (xml/validi-xml? +xsd-polku+ xsd xml) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))

(deftest tarkista-sanoman-validius-harvinaisilla-merkeilla
  (let [xml (html (harja-kuittaus-sanoma/muodosta
                    (str (UUID/randomUUID))
                    123456789
                    "2016-02-11T08:59:30.035Z"
                    "valitetty"
                    (urakan-tiedot "Oulun alueurakka & < 2014-2019")
                    (paivystajat "Päivystäjä > < &")
                    nil))
        xsd "harja-tloik.xsd"]
    (is (xml/validi-xml? +xsd-polku+ xsd xml) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))

(deftest tarkista-tloik-kuittaus-sanoman-validius-validilla-viestilla
  (let [tloik-kuittaus-vastaanotto-xml (slurp "test/resurssit/tloik/tloik-kuittaus-vastaanotto.xml")
        vastaus (tloik-kuittaus-sanoma/lue-kuittaus tloik-kuittaus-vastaanotto-xml)]
    (is (nil? (:virhe vastaus)))))

(deftest tarkista-tloik-kuittaus-sanoman-validius-epavalidilla-viestilla
  (let [;; Tuotannossa, joihinkin viesteihin tulee vastapalloon sama viesti, joka tloikin jonoihin lähetettiin
        ;; Varmistetaan, että saman viestin vastaanotto ei aiheuta "onnistunut" tyyppistä merkintää Harjassa
        tloik-kuittaus-vastaanotto-xml (slurp "test/resurssit/tloik/tloik-valipalikan-kuittaus-viesti.xml")
        vastaus (tloik-kuittaus-sanoma/lue-kuittaus tloik-kuittaus-vastaanotto-xml)]
    (is (not (nil? (:virhe vastaus))))))
