(ns harja.palvelin.integraatiot.sampo-test
  (:require [clojure.test :refer [deftest is]]
            [harja.palvelin.integraatiot.sampo :refer [muodosta-maksuera]]
            [hiccup.core :refer [html]]
            [clojure.java.io :as io])
  (:import (javax.xml.validation SchemaFactory Schema Validator)
           (javax.xml XMLConstants)
           (javax.xml.transform.stream StreamSource)
           (java.io ByteArrayInputStream)
           (org.w3c.dom.ls LSResourceResolver LSInput)
           (org.xml.sax SAXParseException)))

(def +xsd-polku+ "test/xsd/sampo/outbound/")

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
  (let [maksuera (html (muodosta-maksuera))
        xsd "nikuxog_product.xsd"]
    (is (validoi xsd maksuera) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))