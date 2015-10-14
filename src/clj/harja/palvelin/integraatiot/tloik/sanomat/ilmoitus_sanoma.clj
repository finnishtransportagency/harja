(ns harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml])
  (:import (java.text SimpleDateFormat ParseException)
           (java.sql Date)))

(def +xsd-polku+ "xsd/tloik/")

(defn parsi-paivamaara [teksti]
  (if teksti
    (try (new Date (.getTime (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss") teksti)))
         (catch ParseException e
           (log/error e "Virhe parsiessa päivämäärää: " teksti)
           nil))
    nil))

(defn lue-ilmoittaja [ilmoittaja]
  {:etunimi      (z/xml1-> ilmoittaja :etunimi z/text)
   :matkapuhelin (z/xml1-> ilmoittaja :matkapuhelin z/text)
   :sahkoposti   (z/xml1-> ilmoittaja :sahkoposti z/text)
   :sukunimi     (z/xml1-> ilmoittaja :sukunimi z/text)
   :tyopuhelin   (z/xml1-> ilmoittaja :tyopuhelin z/text)
   :tyyppi       (z/xml1-> ilmoittaja :tyyppi z/text)})

(defn lue-lahettaja [lahettaja]
  (when lahettaja
    {:etunimi      (z/xml1-> lahettaja :etunimi z/text)
     :matkapuhelin (z/xml1-> lahettaja :matkapuhelin z/text)
     :sahkoposti   (z/xml1-> lahettaja :sahkoposti z/text)
     :sukunimi     (z/xml1-> lahettaja :sukunimi z/text)
     :tyopuhelin   (z/xml1-> lahettaja :tyopuhelin z/text)}))

(defn lue-selitteet [selitteet]
  (z/xml-> selitteet (fn [selite]
                       (z/xml-> (z/xml1-> selite) :selite z/text))))

(defn lue-sijainti [sijainti]
  {:tienumero (Integer/parseInt (z/xml1-> sijainti :tienumero z/text))
   :x         (Double/parseDouble (z/xml1-> sijainti :x z/text))
   :y         (Double/parseDouble (z/xml1-> sijainti :y z/text))})

(defn lue-vastaanottaja [vastaanottaja]
  {:nimi    (z/xml1-> vastaanottaja :nimi z/text)
   :ytunnus (z/xml1-> vastaanottaja :ytunnus z/text)})

(defn lue-viesti [viesti]
  (when (not (xml/validoi +xsd-polku+ "ilmoitus.xsd" viesti))
    (throw (new RuntimeException "XML-sanoma ei ole XSD-skeeman ilmoitus.xsd mukaan validi.")))

  (let [data (xml/lue viesti)
        ilmoitus {:ilmoitettu         (parsi-paivamaara (z/xml1-> data :ilmoitettu z/text))
                  :ilmoitus-id        (Integer/parseInt (z/xml1-> data :ilmoitusId z/text))
                  :ilmoitustyyppi     (z/xml1-> data :ilmoitustyyppi z/text)
                  :valitettu          (parsi-paivamaara (z/xml1-> data :valitettu z/text))
                  :urakkatyyppi       (z/xml1-> data :urakkatyyppi z/text)
                  :vapaateksti        (z/xml1-> data :vapaateksti z/text)
                  :viesti-id          (z/xml1-> data :viestiId z/text)
                  :yhteydenottopyynto (boolean (Boolean/valueOf (z/xml1-> data :viestiId z/text)))
                  :ilmoittaja         (when-let [ilmoittaja (into {} (z/xml-> data :ilmoittaja lue-ilmoittaja))]
                                        (if (empty? ilmoittaja) nil ilmoittaja))
                  :lahettaja          (when-let [lahettaja (into {} (z/xml-> data :lahettaja lue-lahettaja))]
                                        (if (empty? lahettaja) nil lahettaja))
                  :selitteet          (into [] (z/xml-> data :seliteet lue-selitteet))
                  :sijainti           (when-let [sijainti (into {} (z/xml-> data :sijainti lue-sijainti))]
                                        (if (empty? sijainti) nil sijainti))
                  :vastaanottaja      (when-let [vastaanottaja (into {} (z/xml-> data :vastaanottaja lue-vastaanottaja))]
                                        (if (empty? vastaanottaja) nil vastaanottaja))
                  }]
    ilmoitus))