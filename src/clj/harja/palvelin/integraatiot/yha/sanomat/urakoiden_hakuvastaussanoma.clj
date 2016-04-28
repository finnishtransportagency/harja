(ns harja.palvelin.integraatiot.yha.sanomat.urakoiden-hakuvastaussanoma
  (:require [hiccup.core :refer [html]]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]))

(def +xsd-polku+ "xsd/yha/")

#_(defn lue-kuittaus [viesti]
  (when (not (xml/validoi +xsd-polku+ "yha.xsd" viesti))
    (throw (new RuntimeException "XML-sanoma ei ole XSD-skeeman yha.xsd mukaan validi.")))
  (let [data (xml/lue viesti)
        urakat (mapv (fn [urakka]
                       (hash-map :yha-id (z/xml1-> urakka :yha:yha-id z/text)
                                 :elyt (mapv #(z/xml1-> % :yha:ely z/text) (z/xml-> urakka :yha:elyt))
                                 :vuodet (mapv #(z/xml1-> % :yha:vuosi z/text) (z/xml-> urakka :yha:vuodet))
                                 :tunnus (z/xml1-> urakka :yha:tunnus z/text)
                                 :nimi (z/xml1-> urakka :yha:nimi z/text)))
                     (z/xml-> homma :yha:urakat :yha:urakka))]
    urakat)) ;; FIXME Ei käänny, unable to resolve symbol homma.
