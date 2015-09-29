(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-lisayskutsu
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +xsd-polku+ "xsd/tierekisteri/schemas/")

(defn muodosta-xml-sisalto [{:keys [lisaaja tietue lisatty]}]
  (let [tie (get-in tietue [:sijainti :tie])]
    [:ns2:lisaaTietue {:xmlns:ns2 "http://www.solita.fi/harja/tierekisteri/lisaaTietue"}
     [:lisaaja
      [:henkilo (:henkilo lisaaja)]
      [:jarjestelma (:jarjestelma lisaaja)]
      [:organisaatio (:organisaatio lisaaja)]
      [:yTunnus (:yTunnus lisaaja)]]
     [:tietue
      [:tunniste (:tunniste tietue)]
      [:alkupvm (:alkupvm tietue)]
      (when (:loppupvm tietue) [:loppupvm (:loppupvm tietue)])
      (when (:karttapvm tietue) [:karttapvm (:karttapvm tietue)])
      (when (:piiri tietue) [:piiri (:piiri tietue)])
      (when (:kuntoluokka tietue) [:kuntoluokka (:kuntoluokka tietue)])
      (when (:urakka tietue) [:urakka (:urakka tietue)])
      [:sijainti
       [:tie
        [:numero (:numero tie)]
        [:aet (:aet tie)]
        [:aosa (:aosa tie)]
        (when (:let tie) [:let (:let tie)])
        (when (:losa tie) [:losa (:losa tie)])
        (when (:ajr tie) [:ajr (:ajr tie)])
        (when (:puoli tie) [:puoli (:puoli tie)])
        (when (:alkupvm tie) [:alkupvm (:alkupvm tie)])]]
      [:tietolaji
       [:tietolajitunniste (get-in tietue [:tietolaji :tietolajitunniste])]
       [:arvot (get-in tietue [:tietolaji :arvot])]]]
     [:lisatty lisatty]]))

(defn muodosta-kutsu [tietue]
  (let [sisalto (muodosta-xml-sisalto tietue)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "lisaaTietue.xsd" xml)
      xml
      (do
        (log/error "Tietueenlisäyspyyntöä ei voida lähettää. Pyynnön XML ei ole validi.")
        (throw+
          {:type    :tietueen-lisays-epaonnistui
           :virheet [{:koodi :ei-validi-xml :viesti "Tietueen lisäyspyyntö Tierekisteriin ei ole validi"}]})))))