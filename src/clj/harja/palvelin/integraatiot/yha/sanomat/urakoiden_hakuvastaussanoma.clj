(ns harja.palvelin.integraatiot.yha.sanomat.urakoiden-hakuvastaussanoma
  (:require [hiccup.core :refer [html]]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]))

(def +xsd-polku+ "xsd/yha/")

(defn lue-urakat [data]
  (mapv (fn [urakka]
          (hash-map :yha-id (xml/parsi-kokonaisluku (z/xml1-> urakka :yha:yha-id z/text))
                    :elyt (vec (mapcat #(z/xml-> % :yha:ely z/text) (z/xml-> urakka :yha:elyt)))
                    :vuodet (vec (mapcat #(z/xml-> % :yha:vuosi z/text xml/parsi-kokonaisluku) (z/xml-> urakka :yha:vuodet)))
                    :tunnus (z/xml1-> urakka :yha:tunnus z/text)
                    :sampotunnus (z/xml1-> urakka :yha:sampotunnus z/text)))
        (z/xml-> data :yha:urakat :yha:urakka)))

(defn lue-virhe [data]
  (z/xml1-> data :yha:virhe z/text))

(defn lue-sanoma [viesti]
  (when (not (xml/validoi +xsd-polku+ "yha.xsd" viesti))
    (throw (new RuntimeException "XML-sanoma ei ole XSD-skeeman yha.xsd mukaan validi.")))
  (let [data (xml/lue viesti)
        urakat (lue-urakat data)
        virhe (lue-virhe data)
        vastaus {:urakat urakat}]
    (if virhe
      (assoc vastaus :virhe virhe)
      vastaus)))