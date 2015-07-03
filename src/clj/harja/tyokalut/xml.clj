(ns harja.tyokalut.xml
  (:require [clojure.xml :refer [parse]]
            [clojure.java.io :as io]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log])
  (:import (javax.xml.validation SchemaFactory)
           (javax.xml XMLConstants)
           (javax.xml.transform.stream StreamSource)
           (java.io ByteArrayInputStream)
           (org.w3c.dom.ls LSResourceResolver LSInput)
           (org.xml.sax SAXParseException)
           ))

(defn validoi
  "Validoi annetun XML sisällön vasten annettua XSD-skeemaa. XSD:n tulee olla tiedosto annettussa XSD-polussa. XML on
  String, joka on sisältö."
  [xsd-polku xsd xml]
  (log/debug "Validoidaan XML käyttäen XSD-skeemaa:" xsd ". XML:n sisätö on:" xml)
  (let
    [schema-factory (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)]

    (.setResourceResolver schema-factory
                          (reify LSResourceResolver
                            (resolveResource [this type namespaceURI publicId systemId baseURI]
                              (let [xsd-file (io/file xsd-polku systemId)]
                                (reify LSInput
                                  (getByteStream [_] (io/input-stream xsd-file))
                                  (getPublicId [_] publicId)
                                  (getSystemId [_] systemId)
                                  (getBaseURI [_] baseURI)
                                  (getCharacterStream [_] (io/reader xsd-file))
                                  (getEncoding [_] "UTF-8")
                                  (getStringData [_] (slurp xsd-file)))))))
    (try (-> schema-factory
             (.newSchema (StreamSource. (io/input-stream (io/file xsd-polku xsd))))
             .newValidator
             (.validate (StreamSource. (ByteArrayInputStream. (.getBytes xml)))))
         true
         (catch SAXParseException e
           (log/error "Invalidi XML: " e)
           false))))

(defn lue [xml]
  (let [in (ByteArrayInputStream. (.getBytes xml "UTF-8"))]
    (try (xml-zip (parse in))
         (catch SAXParseException e
           (log/error e "Tapahtui poikkeus luettuessa XML-sisältöä: " xml)
           nil))))