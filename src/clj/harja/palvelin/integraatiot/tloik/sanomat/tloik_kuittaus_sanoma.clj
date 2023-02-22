(ns harja.palvelin.integraatiot.tloik.sanomat.tloik-kuittaus-sanoma
  (:require [hiccup.core :refer [html]]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]))

(def +xsd-polku+ "xsd/tloik/")

(defn lue-kuittaus [viesti]
  (when (not (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" viesti))
    (throw (new RuntimeException "XML-sanoma ei ole XSD-skeeman harja-tloik.xsd mukaan validi.")))
  (let [data (xml/lue viesti)
        kuittaus {:aika           (xml/parsi-aikaleima (z/xml1-> data :aika z/text) "yyyy-MM-dd'T'HH:mm:ss.SSS")
                  :kuittaustyyppi (z/xml1-> data :kuittaustyyppi z/text)
                  :viesti-id      (z/xml1-> data :viestiId z/text)
                  :virhe          (z/xml1-> data :virhe z/text)}
        ;; Jos kuittausviesti on virheellinen muuten kuin olemalla epävalidia, niin kuittaustyyppi jää nilliksi. Varmistetaan se vielä
        kuittaus (if (and (nil? (:kuittaustyyppi kuittaus)) (nil? (:virhe kuittaus)))
                   (assoc kuittaus :virhe "Kuittaustyyppi puuttuu")
                   kuittaus)]
    kuittaus))
