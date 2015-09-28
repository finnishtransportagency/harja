(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-paivityskutsu
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +xsd-polku+ "xsd/tierekisteri/schemas/")

(defn muodosta-xml-sisalto [tiedot]
  [:ns2:paivitaTietue {:xmlns:ns2 "http://www.solita.fi/harja/tierekisteri/paivitaTietue"}
   [:paivittaja
    [:henkilo (get-in tiedot [:lisaaja :henkilo])]
    [:jarjestelma (get-in tiedot [:lisaaja :jarjestelma])]
    [:organisaatio (get-in tiedot [:lisaaja :organisaatio])]
    [:yTunnus (get-in tiedot [:lisaaja :yTunnus])]]
   [:tietue
    [:tunniste (get-in tiedot [:tietue :tunniste])]
    [:alkupvm (get-in tiedot [:tietue :alkupvm])]
    [:loppupvm (get-in tiedot [:tietue :loppupvm])]
    [:karttapvm (get-in tiedot [:tietue :karttapvm])]
    [:piiri (get-in tiedot [:tietue :piiri])]
    [:kuntoluokka (get-in tiedot [:tietue :kuntoluokka])]
    [:urakka (get-in tiedot [:tietue :urakka])]
    [:sijainti
     [:tie
      [:numero (get-in tiedot [:tietue :sijainti :tie :numero])]
      [:aet (get-in tiedot [:tietue :sijainti :tie :aet])]
      [:aosa (get-in tiedot [:tietue :sijainti :tie :aosa])]
      [:let (get-in tiedot [:tietue :sijainti :tie :let])]
      [:losa (get-in tiedot [:tietue :sijainti :tie :losa])]
      [:ajr (get-in tiedot [:tietue :sijainti :tie :ajr])]
      [:puoli (get-in tiedot [:tietue :sijainti :tie :puoli])]
      [:alkupvm (get-in tiedot [:tietue :sijainti :tie :alkupvm])]]]
    [:tietolaji
     [:tietolajitunniste (get-in tiedot [:tietue :tietolaji :tietolajitunniste])]
     [:arvot (get-in tiedot [:tietue :tietolaji :arvot])]]]
   [:paivitetty (:paivitetty tiedot)]])

(defn muodosta-kutsu [tietue]
  (let [sisalto (muodosta-xml-sisalto tietue)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "paivitaTietue.xsd" xml)
      xml
      (do
        (log/error "Tietueenpäivityspyyntöä ei voida lähettää. Pyynnön XML ei ole validi.")
        (throw+
          {:type    :tietueen-lisays-epaonnistui
           :virheet [{:koodi :ei-validi-xml :viesti "Tietueen päivityspyyntö Tierekisteriin ei ole validi"}]})))))