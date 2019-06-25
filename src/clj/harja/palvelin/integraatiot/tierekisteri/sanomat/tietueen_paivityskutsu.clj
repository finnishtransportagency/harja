(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-paivityskutsu
  "Käytetään muodostamaan tietueen päivityksen XML-sanoma annetusta Clojure-datasta. Validoi muodostetun sanoman
   Tierekisterin xsd-skeemaa vasten, joten tuotettu XML on aina sen mukaista."
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.elementit :as elementit]
            [hiccup.core :refer [html]])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +xsd-polku+ "xsd/tierekisteri/skeemat/")

(defn muodosta-xml-sisalto [{:keys [paivittaja tietue paivitetty]}]
  [:ns2:paivitaTietue {:xmlns:ns2 "http://www.solita.fi/harja/tierekisteri/paivitaTietue"}
   [:paivittaja
    [:henkilo (:henkilo paivittaja)]
    [:jarjestelma (:jarjestelma paivittaja)]
    [:organisaatio (:organisaatio paivittaja)]
    [:yTunnus (:yTunnus paivittaja)]]
   (elementit/muodosta-tietue tietue)
   [:paivitetty paivitetty]])

(defn muodosta-kutsu [tietue]
  (let [sisalto (muodosta-xml-sisalto tietue)
         xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validi-xml? +xsd-polku+ "paivitaTietue.xsd" xml)
      xml
      (do
        (log/error "Tietueenpäivityspyyntöä ei voida lähettää. Pyynnön XML ei ole validi.")
        (throw+
          {:type    :tietueen-lisays-epaonnistui
           :virheet [{:koodi :ei-validi-xml :viesti "Tietueen päivityspyyntö Tierekisteriin ei ole validi"}]})))))
