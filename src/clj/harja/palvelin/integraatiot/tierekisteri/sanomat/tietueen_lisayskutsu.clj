(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-lisayskutsu
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +xsd-polku+ "xsd/tierekisteri/schemas/")

(defn muodosta-xml-sisalto [tietue]
  [:ns2:lisaaTietue {:xmlns:ns2 "http://www.solita.fi/harja/tierekisteri/lisaaTietue"}
   [:lisaaja
    [:henkilo "Keijo Käsittelijä"]
    [:jarjestelma "FastMekka"]
    [:organisaatio "Asfaltia Oy"]
    [:yTunnus "1234567-8"]]
   [:tietue
    [:tunniste "1245rgfsd"]
    [:alkupvm "2015-03-03+02:00"]
    [:loppupvm "2015-03-03+02:00"]
    [:karttapvm "2015-03-03+02:00"]
    [:piiri "1"]
    [:kuntoluokka "1"]
    [:urakka "100"]
    [:sijainti
     [:tie
      [:numero "1"]
      [:aet "1"]
      [:aosa "1"]
      [:let "1"]
      [:losa "1"]
      [:ajr "1"]
      [:puoli "1"]
      [:alkupvm "2017-03-03+02:00"]]]
    [:tietolaji
     [:tietolajitunniste "tl506"]
     [:arvot "998 2 0 1 0 1 1 Testi liikennemerkki Omistaja O 4 123456789 40"]]]
   [:lisatty "2015-05-26+03:00"]])

(defn muodosta-kutsu [tietue]
  (let [sisalto (muodosta-xml-sisalto tietue)
        xml (xml/tee-xml-sanoma sisalto)
        _ (log/debug "Lähetettävä XML luotu: " (pr-str xml))]
    (if (xml/validoi +xsd-polku+ "lisaaTietue.xsd" xml)
      xml
      (do
        (log/error "Tietueenlisäyspyyntöä ei voida lähettää. Pyynnön XML ei ole validi.")
        (throw+
          {:type    :tietueen-lisays-epaonnistui
           :virheet [{:koodi :ei-validi-xml :viesti "Tietueen lisäyspyyntö Tierekisteriin ei ole validi"}]})))))