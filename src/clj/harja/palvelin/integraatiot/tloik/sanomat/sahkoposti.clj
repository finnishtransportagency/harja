(ns harja.palvelin.integraatiot.tloik.sanomat.sahkoposti
  (:require [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]))

(def ^:const +xsd-polku+ "xsd/sahkoposti/")
(def ^:const +sahkoposti-xsd+ "sahkoposti.xsd")

(defn- validoi [xml-viesti]
  (xml/validoi +xsd-polku+ +sahkoposti-xsd+ xml-viesti))

(defn- lue-sahkoposti-xml [xml-viesti]
  (validoi xml-viesti)
  (let [x (xml/lue xml-viesti)]
    (fn [& polku]
      (apply z/xml1-> x (concat polku [z/text])))))

(defn lue-sahkoposti [xml-viesti]
  (let [v (lue-sahkoposti-xml xml-viesti)]
    {:viesti-id (v :viestiId)
     :vastaanottaja (v :vastaanottajat
                        :vastaanottaja)
     :lahettaja (v :lahettaja)
     :otsikko (v :otsikko)
     :sisalto (v :sisalto)}))
