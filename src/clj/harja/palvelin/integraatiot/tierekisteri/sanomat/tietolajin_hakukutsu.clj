(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietolajin-hakukutsu
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/tierekisteri/skeemat/")

(defn muodosta-xml-sisalto [tunniste muutospvm]
  [:haeTietolaji
   [:tietolajitunniste tunniste]
   (when (not (nil? muutospvm)) [:muutospvm (xml/formatoi-paivamaara muutospvm)])])

(defn muodosta-kutsu [tunniste muutospvm]
  (let [sisalto (muodosta-xml-sisalto tunniste muutospvm)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validi-xml? +xsd-polku+ "haeTietolaji.xsd" xml)
      xml
      (do
        (log/error "Tietolajihakukutsua ei voida lähettää. Kutsu XML ei ole validi.")
        (throw+
          {:type    :tietolaji-haku-epaonnistui
           :virheet [{:koodi :ei-validi-xml :viesti "Tietolajin hakukutsu Tierekisteriin ei ole validi"}]})))))
