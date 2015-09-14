(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-hakukutsu
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]]))

(def +xsd-polku+ "xsd/tierekisteri/schemas/")

(defn muodosta-viesti [tunniste tietolajitunniste]
  [:ns2:haeTietue
   {:xmlns:ns2 "http://www.solita.fi/harja/tierekisteri/haeTietue"}
   [:tunniste tunniste]
   [:tietolajitunniste tietolajitunniste]])

(defn muodosta [tunniste tietolajitunniste]
  (let [sisalto (muodosta-viesti tunniste tietolajitunniste)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "haeTietue.xsd" xml)
      xml
      (do
        (log/error "Tietueenhakukutsua ei voida lähettää. Kutsu XML ei ole validi.")
        (throw (Exception. "Tietueen hakukutsu Tierekisteriin ei ole validi"))))))