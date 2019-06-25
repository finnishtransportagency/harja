(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-poistokutsu
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +xsd-polku+ "xsd/tierekisteri/skeemat/")

(defn muodosta-xml-sisalto [tiedot]
  [:ns2:poistaTietue {:xmlns:ns2 "http://www.solita.fi/harja/tierekisteri/poistaTietue"}
   [:poistaja
    [:henkilo (get-in tiedot [:poistaja :henkilo])]
    [:jarjestelma (get-in tiedot [:poistaja :jarjestelma])]
    [:organisaatio (get-in tiedot [:poistaja :organisaatio])]
    [:yTunnus (get-in tiedot [:poistaja :yTunnus])]]
   [:tunniste (:tunniste tiedot)]
   [:tietolajitunniste (:tietolajitunniste tiedot)]
   [:poistettu (:poistettu tiedot)]])

(defn muodosta-kutsu [tietue]
  (let [sisalto (muodosta-xml-sisalto tietue)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validi-xml? +xsd-polku+ "poistaTietue.xsd" xml)
      xml
      (do
        (log/error "Tietueenpoistopyyntöä ei voida lähettää. Pyynnön XML ei ole validi.")
        (throw+
          {:type    :tietueen-lisays-epaonnistui
           :virheet [{:koodi :ei-validi-xml :viesti "Tietueen poistopyyntö Tierekisteriin ei ole validi"}]})))))
