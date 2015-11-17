(ns harja.palvelin.integraatiot.tloik.sanomat.tloik-kuittaus-sanoma
  (:require [hiccup.core :refer [html]]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]))

(def +xsd-polku+ "xsd/tloik/")

(defn lue-kuittaus [viesti]
  (when (not (xml/validoi +xsd-polku+ "harja-tloik.xsd" viesti))
    (throw (new RuntimeException "XML-sanoma ei ole XSD-skeeman harja-tloik.xsd mukaan validi.")))
  (let [data (xml/lue viesti)
        kuittaus {:aika           (xml/parsi-paivamaara (z/xml1-> data :aika z/text))
                  :kuittaustyyppi (z/xml1-> data :kuittaustyyppi z/text)
                  :viesti-id      (z/xml1-> data :viestiId z/text)}]
    kuittaus))
