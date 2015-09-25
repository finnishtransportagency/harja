(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-lisayskutsu
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +xsd-polku+ "xsd/tierekisteri/schemas/")

(defn muodosta-viesti [tietue]
  ; FIXME Skeeman mukainen requesti
  [:ns2:lisaaTietue
   {:xmlns:ns2 "http://www.solita.fi/harja/tierekisteri/haeTietue"}])

(defn muodosta [tietue]
  (let [sisalto (muodosta-viesti tietue)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "lisaaTietue.xsd" xml)
      xml
      (do
        (log/error "Tietueenlisäyspyyntöä ei voida lähettää. Pyynnön XML ei ole validi.")
        (throw+
          {:type    :tietueen-lisays-epaonnistui
           :virheet [{:koodi :ei-validi-xml :viesti "Tietueen lisäyspyyntö Tierekisteriin ei ole validi"}]})))))