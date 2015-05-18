(ns harja.palvelin.integraatiot.sampo.kuittaus
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [taoensso.timbre :as log])
  (:import (java.io ByteArrayInputStream)
           (org.xml.sax SAXParseException)))


(defn lue-xml [xml]
  (let [in (ByteArrayInputStream. (.getBytes xml "UTF-8"))]
    (try (xml-zip (parse in))
         (catch SAXParseException e
           (log/error e "Virheellinen XML-kuittaus vastaanotettu Samposta. XML: " xml)
           nil))))

(defn onnistunut? [xml]
  (= "SUCCESS" (z/xml1-> xml :Status (z/attr :state))))

(defn viesti-id [xml]
  (z/xml1-> xml :Object (z/attr :messageId)))

(defn hae-virheet [xml]
  (z/xml-> xml :Records :Record :ErrorInformation
           (fn [error-information]
             {:vakavuus (z/xml1-> error-information :Severity z/text)
              :kuvaus   (z/xml1-> error-information :Description z/text)})))

;; Todo: lisää kutsuvaan päähän logitus
(defn lue-kuittaus [kuittaus-xml]
  (if-let [xml (lue-xml kuittaus-xml)]
    ;; Huom. root-elementti voi vaihtua!
    (let [xml (or (z/xml1-> xml :XOGOutput) xml)]
      (if (onnistunut? xml)
        {:viesti-id (viesti-id xml)}
        {:virhe   :sampo-raportoi-virheita
         :virheet (hae-virheet xml)}))
    {:virhe :kuittaus-xml-ei-validi}))
