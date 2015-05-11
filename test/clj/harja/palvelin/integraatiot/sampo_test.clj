(ns harja.palvelin.integraatiot.sampo-test
  (:require [clojure.test :refer [deftest is]]
            [harja.palvelin.integraatiot.sampo :refer [muodosta-maksuera]]
            [hiccup.core :refer [html]]
            [clojure.java.io :as io]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z])
  (:import (javax.xml.validation SchemaFactory Schema Validator)
           (javax.xml XMLConstants)
           (javax.xml.transform.stream StreamSource)
           (java.io ByteArrayInputStream)
           (org.w3c.dom.ls LSResourceResolver LSInput)
           (org.xml.sax SAXParseException)
           (java.text SimpleDateFormat)))

(def +xsd-polku+ "test/xsd/sampo/outbound/")

(defn parsi-paivamaara [teksti]
  (.parse (SimpleDateFormat. "dd.MM.yyyy") teksti))

(def +maksuera+ {:nimi                "Testimaksuera"
                 :numero              123456789
                 :toimenpideinstanssi {
                                       :alkupvm          (parsi-paivamaara "12.12.2015")
                                       :loppupvm         (parsi-paivamaara "1.1.2017")
                                       :vastuuhenkilo_id "A009717"
                                       :talousosasto_id  "talousosasto"
                                       :tuotepolku       "polku/tuote"}
                 :urakka              {
                                       :sampoid "PR00020606"}})

(defn validoi [xsd xml]
  (let
    [schema-factory (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)]

    (.setResourceResolver schema-factory
                          (reify LSResourceResolver
                            (resolveResource [this type namespaceURI publicId systemId baseURI]
                              (let [xsd-file (io/file +xsd-polku+ systemId)]
                                (reify LSInput
                                  (getByteStream [_] (io/input-stream xsd-file))
                                  (getPublicId [_] publicId)
                                  (getSystemId [_] systemId)
                                  (getBaseURI [_] baseURI)
                                  (getCharacterStream [_] (io/reader xsd-file))
                                  (getEncoding [_] "UTF-8")
                                  (getStringData [_] (slurp xsd-file)))))))
    (try (-> schema-factory
             (.newSchema (StreamSource. (io/input-stream (io/file +xsd-polku+ xsd))))
             .newValidator
             (.validate (StreamSource. (ByteArrayInputStream. (.getBytes xml)))))
         true
         (catch SAXParseException e
           (println "Invalidi XML: " e)
           false))))

(deftest tarkista-maksueran-validius
  (let [maksuera (html (muodosta-maksuera +maksuera+))
        xsd "nikuxog_product.xsd"]
    (is (validoi xsd maksuera) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))

(deftest tarkista-maksueran-sisalto
  (let [maksuera-xml (xml-zip (parse (ByteArrayInputStream. (.getBytes (html (muodosta-maksuera +maksuera+)) "UTF-8"))))]
    (is (= "2015-12-12T00:00:00.0" (z/xml1-> maksuera-xml :Products :Product (z/attr :start))))
    (is (= "2017-01-01T00:00:00.0" (z/xml1-> maksuera-xml :Products :Product (z/attr :finish))))
    (is (= "A009717" (z/xml1-> maksuera-xml :Products :Product (z/attr :managerUserName))))
    (is (= "Testimaksuera" (z/xml1-> maksuera-xml :Products :Product (z/attr :name))))
    (is (= "HA123456789" (z/xml1-> maksuera-xml :Products :Product (z/attr :objectID))))
    (is (= "PR00020606" (z/xml1-> maksuera-xml :Products :Product :InvestmentAssociations :Allocations :ParentInvestment (z/attr :InvestmentID))))
    (is (= "kulu2015" (z/xml1-> maksuera-xml :Products :Product :InvestmentTasks :Task :Assignments :TaskLabor (z/attr :resourceID))))
    (is (= "Testimaksuera" (z/xml1-> maksuera-xml :Products :Product :InvestmentTasks :Task (z/attr :name))))))