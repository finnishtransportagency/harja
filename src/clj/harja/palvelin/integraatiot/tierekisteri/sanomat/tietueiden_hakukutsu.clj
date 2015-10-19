(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietueiden-hakukutsu
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +xsd-polku+ "xsd/tierekisteri/schemas/")

(defn muodosta-xml-sisalto [tierekisteriosoitevali tietolajitunniste voimassaolopvm]
  [:ns2:haeTietueet
   {:xmlns:ns2 "http://www.solita.fi/harja/tierekisteri/haeTietueet"}
   [:tietolajitunniste tietolajitunniste]
   (when voimassaolopvm [:voimassaolopvm voimassaolopvm])
   (into [:tie] (map (fn[[avain arvo]] [avain arvo]) tierekisteriosoitevali))])

(defn muodosta-kutsu [tierekisteriosoitevali tietolajitunniste voimassaolopvm]
  (let [sisalto (muodosta-xml-sisalto tierekisteriosoitevali tietolajitunniste voimassaolopvm)
        xml (xml/tee-xml-sanoma sisalto)]
    (log/debug (pr-str xml))
    (if (xml/validoi +xsd-polku+ "haeTietueet.xsd" xml)
      xml
      (do
        (log/error "Tietueiden hakukutsua ei voida lähettää. Kutsu XML ei ole validi.")
        (throw+
          {:type    :tietueiden-haku-epaonnistui
           :virheet [{:koodi :ei-validi-xml :viesti "Tietueiden hakukutsu Tierekisteriin ei ole validi"}]})))))