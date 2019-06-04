(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-lisayskutsu
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.elementit :as elementit]
            [hiccup.core :refer [html]])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +xsd-polku+ "xsd/tierekisteri/skeemat/")

(defn muodosta-xml-sisalto [{:keys [lisaaja tietue lisatty]}]
  [:ns2:lisaaTietue {:xmlns:ns2 "http://www.solita.fi/harja/tierekisteri/lisaaTietue"}
   [:lisaaja
    [:henkilo (:henkilo lisaaja)]
    [:jarjestelma (:jarjestelma lisaaja)]
    [:organisaatio (:organisaatio lisaaja)]
    [:yTunnus (:yTunnus lisaaja)]]
   (elementit/muodosta-tietue tietue)
   [:lisatty lisatty]])

(defn muodosta-kutsu [tietue]
  (let [sisalto (muodosta-xml-sisalto tietue)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validi-xml? +xsd-polku+ "lisaaTietue.xsd" xml)
      xml
      (do
        (log/error "Tietueenlisäyspyyntöä ei voida lähettää. Pyynnön XML ei ole validi.")
        (throw+
          {:type    :tietueen-lisays-epaonnistui
           :virheet [{:koodi :ei-validi-xml :viesti "Tietueen lisäyspyyntö Tierekisteriin ei ole validi"}]})))))
