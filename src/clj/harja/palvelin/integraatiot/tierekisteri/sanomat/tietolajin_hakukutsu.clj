(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietolajin-hakukutsu
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]]))

(def +xsd-polku+ "xsd/tierekisteri/schemas/")

(defn muodosta-viesti [tunniste muutospvm]
  [:haeTietolajit
   [:tietolajitunniste tunniste]
   (when (not (nil? muutospvm)) [:muutospvm (xml/formatoi-paivamaara muutospvm)])])

(defn muodosta [tunniste muutospvm]
  (let [sisalto (muodosta-viesti tunniste muutospvm)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "haeTietolaji.xsd" xml)
      xml
      (do
        (log/error "Tietolajihakukutsua ei voida lähettää. Kutsu XML ei ole validi.")
        (throw (Exception. "Tietolajin hakukutsu Tierekisteriin ei ole validi"))))))