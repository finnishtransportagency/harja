(ns harja.palvelin.integraatiot.tloik.sanomat.ilmoitustoimenpide-sanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitustoimenpide-sanoma :as ilmoitustoimenpide-sanoma])
  (:import (java.io ByteArrayInputStream)
           (java.text SimpleDateFormat)
           (java.util UUID)))

(def +xsd-polku+ "xsd/tloik/")

(defn parsi-paivamaara [teksti]
  (.parse (SimpleDateFormat. "dd.MM.yyyy") teksti))

(def +ilmoitustoimenpide+
  {:ilmoitusid 12345,
   :vapaateksti "Soitan kunhan kerkeän <TESTI>",
   :kuittaustyyppi "vastaus",
   :kasittelija
   {:matkapuhelin "04428121283",
    :organisaatio "Välittävä Urakoitsija",
    :ytunnus "Y1234",
    :sahkoposti "usko.untamo@valittavaurakoitsija.fi",
    :etunimi "Usko<Toivo",
    :sukunimi "Untamo",
    :tyopuhelin "0509288383"},
   :id 2,
   :kuitattu #inst "2005-09-30T21:10:34.500000000-00:00",
   :kuittaaja
   {:sukunimi "Käsittelijä",
    :tyopuhelin "0509288383",
    :organisaatio "Organi & saatio RY",
    :ytunnus "1234567-8",
    :etunimi "Keijo",
    :matkapuhelin "04428121283",
    :sahkoposti "keijo.kasittelija@eioleolemassa.fi"}})

(deftest tarkista-sanoman-validius
  (let [xml (html (ilmoitustoimenpide-sanoma/muodosta +ilmoitustoimenpide+ (str (UUID/randomUUID))))
        xsd "harja-tloik.xsd"]
    (is (xml/validi-xml? +xsd-polku+ xsd xml) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))

(deftest tarkista-sisalto
  (let [xml (html (ilmoitustoimenpide-sanoma/muodosta +ilmoitustoimenpide+ (str (UUID/randomUUID))))
        data (xml-zip (parse (ByteArrayInputStream. (.getBytes xml "UTF-8"))))]
    (is (= "12345" (z/xml1-> data :ilmoitusId z/text)))
    (is (= "Soitan kunhan kerkeän <TESTI>" (z/xml1-> data :vapaateksti z/text)))
    (is (= "vastaus" (z/xml1-> data :tyyppi z/text)))
    (is (= "Usko<ToivoUntamo04428121283usko.untamo@valittavaurakoitsija.fiVälittävä UrakoitsijaY1234" (z/xml1-> data :kasittelija z/text)))
    (is (= "KeijoKäsittelijä04428121283keijo.kasittelija@eioleolemassa.fiOrgani & saatio RY1234567-8" (z/xml1-> data :ilmoittaja z/text)))
    (is (= "2005-09-30T21:10:34.500" (z/xml1-> data :aika z/text)))))

(deftest nimen-ja-organisaation-escape-kasittely


  )
