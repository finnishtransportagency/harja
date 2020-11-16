(ns harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma
  (:require [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z])
  (:import (java.text SimpleDateFormat)
           (java.util Date)))

(def +xsd-polku+ "xsd/sampo/inbound/")

(defn formatoi-paivamaara [paivamaara]
  (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.S") paivamaara))

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn muodosta-viesti [viesti-id viestityyppi virhekoodi virheviesti]
  [:Harja2SampoAck
   [:Ack
    {:ErrorCode    virhekoodi,
     :ErrorMessage virheviesti,
     :MessageId    viesti-id,
     :ObjectType   viestityyppi,
     :Date         (formatoi-paivamaara (new Date))}]])

(defn muodosta [viesti-id viestityyppi virhekoodi virheviesti]
  (let [sisalto (muodosta-viesti viesti-id viestityyppi virhekoodi virheviesti)
        xml (tee-xml-sanoma sisalto)]
    (if (xml/validi-xml? +xsd-polku+ "HarjaToSampoAcknowledgement.xsd" xml)
      xml
      (do
        (log/error (format "Kuittausta Sampoon ei voida lähettää viesti id:lle %s. Kuittaus XML ei ole validi."
                           viesti-id))
        nil))))

(defn muodosta-onnistunut-kuittaus [viesti-id viestityyppi]
  (muodosta viesti-id viestityyppi "NA" ""))

(defn muodosta-muu-virhekuittaus [viesti-id viestityyppi virheviesti]
  (muodosta viesti-id viestityyppi "CUSTOM" virheviesti))

(defn muodosta-invalidi-xml-virhekuittaus [viesti-id viestityyppi virheviesti]
  (muodosta viesti-id viestityyppi "INVALID_XML" virheviesti))

(defn muodosta-puuttuva-suhde-virhekuittaus [viesti-id viestityyppi virheviesti]
  (muodosta viesti-id viestityyppi "MISSING_RELATION" virheviesti))

(defn onko-kuittaus-positiivinen? [kuittaus]
  (= "NA" (first (z/xml-> (xml/lue kuittaus) (fn [kuittaus] (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :ErrorCode)))))))
