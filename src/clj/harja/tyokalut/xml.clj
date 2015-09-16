(ns harja.tyokalut.xml
  (:require [clojure.xml :refer [parse]]
            [clojure.java.io :as io]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.string :as str])
  (:import (javax.xml.validation SchemaFactory)
           (javax.xml XMLConstants)
           (javax.xml.transform.stream StreamSource)
           (java.io ByteArrayInputStream)
           (org.w3c.dom.ls LSResourceResolver LSInput)
           (org.xml.sax SAXParseException)
           (java.text SimpleDateFormat ParseException)
           (java.util Date)))

(defn listaa-xsd-tiedostot [polku]
  (let [xsdt (mapcat (fn [f]
                       (if (.isDirectory f)
                         (listaa-xsd-tiedostot f)
                         (if-not (.endsWith (.getName f) ".xsd")
                           nil
                           [[(.getName f) (.getAbsolutePath f)]])))
                     (seq (.listFiles polku)))]
    (zipmap (map first xsdt)
            (map second xsdt))))

;; Validointi hakee rekursiivisesti kaikki xsd-tiedostot annetun polun alta
;; on tärkeää välttää duplikaattinimiä tiedostoissa. Esimerkiksi xsd/tierekisteri/schemas ja
;; xsd/jokumuu/schemas saavat sisältää samannimisiä tiedostoja, mutta tierekisteri/schemas sisällä tiedostoilla
;; pitää olla uniikit nimet.
;; Ratkaisuun päädyttiin koska aiempi versio ei tukenut tilanteita, joissa xsd keskenäiset riippuvuudet menivät
;; syvemmälle kuin 1 taso (sijainti->tie->puoli)
(defn validoi
  "Validoi annetun XML sisällön vasten annettua XSD-skeemaa. XSD:n tulee olla tiedosto annettussa XSD-polussa. XML on
  String, joka on sisältö."
  [xsd-polku xsd xml]
  (log/debug "Validoidaan XML käyttäen XSD-skeemaa:" xsd ". XML:n sisältö on:" xml)
  (let [xsd-tiedostot (listaa-xsd-tiedostot (io/as-file (io/resource xsd-polku)))
        schema-factory (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)]


    (.setResourceResolver schema-factory
                            (reify LSResourceResolver
                              (resolveResource [this type namespaceURI publicId systemId baseURI]
                                (let [xsd-file (xsd-tiedostot (last (str/split systemId #"/")))]
                                  (reify LSInput
                                    (getByteStream [_] (io/input-stream xsd-file))
                                    (getPublicId [_] publicId)
                                    (getSystemId [_] systemId)
                                    (getBaseURI [_] baseURI)
                                    (getCharacterStream [_] (io/reader xsd-file))
                                    (getEncoding [_] "UTF-8")
                                    (getStringData [_] (slurp xsd-file)))))))

    (try (-> schema-factory
             (.newSchema (StreamSource. (io/input-stream (io/resource (str xsd-polku xsd)))))
             .newValidator
             (.validate (StreamSource. (ByteArrayInputStream. (.getBytes xml)))))
         true
         (catch SAXParseException e
           (log/error "Invalidi XML: " e)
           false))))

(defn lue
  ([xml] (lue xml "UTF-8"))
  ([xml charset]
   (let [in (ByteArrayInputStream. (.getBytes xml charset))]
     (try (xml-zip (parse in))
          (catch SAXParseException e
            (log/error e "Tapahtui poikkeus luettuessa XML-sisältöä: " xml)
            nil)))))

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn formatoi-aikaleima [paivamaara]
  (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.S") paivamaara))

(defn formatoi-paivamaara [paivamaara]
  (.format (SimpleDateFormat. "yyyy-MM-dd") paivamaara))

(defn parsi-kokonaisluku [data]
  (when (and data (not (empty? data))) (Integer/parseInt data)))

(defn parsi-reaaliluku [data]
  (when (and data (not (empty? data))) (Double/parseDouble data)))

(defn parsi-totuusarvo [data]
  (when (and data (not (empty? data))) (Boolean/parseBoolean data)))

(defn parsi-avain [data]
  (when (and data (not (empty? data))) (keyword (str/lower-case data))))

(defn parsi-aika [formaatti data]
  (when (and data (not (empty? data)))
    (try (new Date (.getTime (.parse (SimpleDateFormat. formaatti) data)))
         (catch ParseException e
           (log/error e "Virhe parsiessa päivämäärää: " data ", formaatilla: " formaatti)
           nil))))

(defn parsi-aikaleima [teksti]
  (parsi-aika "yyyy-MM-dd'T'HH:mm:ss.SSS" teksti))

(defn parsi-paivamaara [teksti]
  (parsi-aika "yyyy-MM-dd" teksti))