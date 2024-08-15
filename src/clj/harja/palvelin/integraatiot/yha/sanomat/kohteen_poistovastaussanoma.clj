(ns harja.palvelin.integraatiot.yha.sanomat.kohteen-poistovastaussanoma
  (:require [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/yha/")

(defn lue-virheet [data]
  (mapv (fn [virhe]
          {:kohde-yha-id (z/xml1-> virhe :kohde-yha-id z/text xml/parsi-kokonaisluku)
           :selite       (z/xml1-> virhe :selite z/text)})
        (z/xml-> data :virhe)))

(defn lue-sanoma [viesti]
  (when (not (xml/validi-xml? +xsd-polku+ "yha.xsd" viesti))
    (throw (new RuntimeException "XML-sanoma ei ole XSD-skeeman yha.xsd mukaan validi.")))
  (let [data (xml/lue viesti)
        virheet (lue-virheet data)]
    {:onnistunut? (empty? virheet)
     :virheet virheet}))
