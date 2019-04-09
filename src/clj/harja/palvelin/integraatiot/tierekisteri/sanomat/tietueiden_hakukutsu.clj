(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietueiden-hakukutsu
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +xsd-polku+ "xsd/tierekisteri/skeemat/")

(defn muodosta-xml-sisalto [tierekisteriosoitevali tietolajitunniste voimassaolopvm tilannepvm]
  [:ns2:haeTietueet
   {:xmlns:ns2 "http://www.solita.fi/harja/tierekisteri/haeTietueet"}
   [:tietolajitunniste tietolajitunniste]
   (when voimassaolopvm [:voimassaolopvm (pvm/pvm->iso-8601 voimassaolopvm)])
   [:tie
    (map (fn [[avain arvo]] [avain arvo]) tierekisteriosoitevali)
    (when tilannepvm [:tilannepvm (pvm/pvm->iso-8601 tilannepvm)])]])

(defn muodosta-kutsu [tierekisteriosoitevali tietolajitunniste voimassaolopvm tilannepvm]
  (let [sisalto (muodosta-xml-sisalto tierekisteriosoitevali tietolajitunniste voimassaolopvm tilannepvm)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validi-xml? +xsd-polku+ "haeTietueet.xsd" xml)
      xml
      (do
        (log/error "Tietueiden hakukutsua ei voida lähettää. Kutsu XML ei ole validi.")
        (throw+
          {:type :tietueiden-haku-epaonnistui
           :virheet [{:koodi :ei-validi-xml :viesti "Tietueiden hakukutsu Tierekisteriin ei ole validi"}]})))))
