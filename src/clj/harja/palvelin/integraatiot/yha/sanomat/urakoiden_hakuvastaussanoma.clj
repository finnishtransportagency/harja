(ns harja.palvelin.integraatiot.yha.sanomat.urakoiden-hakuvastaussanoma
  (:require [hiccup.core :refer [html]]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]))

(def +xsd-polku+ "xsd/yha/")

(defn lue-urakat [data]
  (mapv (fn [urakka]
          (hash-map :yhaid (xml/parsi-kokonaisluku (z/xml1-> urakka :yha-id z/text))
                    :elyt (vec (mapcat #(z/xml-> % :ely z/text) (z/xml-> urakka :elyt)))
                    :vuodet (vec (mapcat #(z/xml-> % :vuosi z/text xml/parsi-kokonaisluku) (z/xml-> urakka :vuodet)))
                    :yhatunnus (z/xml1-> urakka :tunnus z/text)
                    :sampotunnus (z/xml1-> urakka :sampotunnus z/text)))
        (z/xml-> data :urakat :urakka)))

(defn lue-virhe [data]
  (z/xml1-> data :virhe z/text))

(defn lue-sanoma [viesti]
  (when (not (xml/validi-xml? +xsd-polku+ "yha.xsd" viesti))
    (throw (new RuntimeException "XML-sanoma ei ole XSD-skeeman yha.xsd mukaan validi.")))
  (let [data (xml/lue viesti)
        urakat (lue-urakat data)
        virhe (lue-virhe data)
        vastaus {:urakat urakat}]
    (if virhe
      (assoc vastaus :virhe virhe)
      vastaus)))
