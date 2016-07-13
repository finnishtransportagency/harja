(ns harja.palvelin.integraatiot.tierekisteri.sanomat.urakan-tietueiden-hakukutsu
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +xsd-polku+ "xsd/tierekisteri/skeemat/")

(defn muodosta-xml-sisalto [urakka tietolajitunniste tilannepvm]
  [:ns2:haeUrakanTietueet
   {:xmlns:ns2 "http://www.solita.fi/harja/tierekisteri/haeUrakanTietueet"}
   [:urakka-id urakka]
   [:tilannepvm tilannepvm]
   [:tietolaji tietolajitunniste]])

(defn muodosta-kutsu [urakka tietolajitunniste tilannepvm]
  (let [sisalto (muodosta-xml-sisalto urakka tietolajitunniste tilannepvm)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "haeUrakanTietueet.xsd" xml)
      xml
      (do
        (log/error "Urakan tietueiden hakukutsua ei voida lähettää. Kutsu XML ei ole validi.")
        (throw+
          {:type :tietueiden-haku-epaonnistui
           :virheet [{:koodi :ei-validi-xml :viesti "Urakan tietueiden hakukutsu Tierekisteriin ei ole validi"}]})))))