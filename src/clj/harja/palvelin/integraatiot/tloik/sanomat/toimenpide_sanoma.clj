(ns harja.palvelin.integraatiot.tloik.sanomat.toimenpide-sanoma
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]]))

(def +xsd-polku+ "xsd/tloik/")

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn muodosta-viesti []
  [:harja:toimenpide
   {:xmlns:harja "http://www.liikennevirasto.fi/xsd/harja"}
   [:viestiId
    "123412343"]
   [:ilmoitusId
    "3213123"]
   [:tyyppi
    "vastaus"]
   [:aika
    "2015-09-29T04:49:45"]
   [:vapaateksti
    "Antin Auraus Oy hoitaa auraustyön."]
   [:kasittelija
    [:henkilo
     [:etunimi
      "Antti"]
     [:sukunimi
      "Auraaja"]
     [:matkapuhelin
      "09823489"]
     [:sahkoposti
      "antti.auraaja@antinauraus.fi"]]
    [:organisaatio
     [:nimi
      "Antin Auraus Oy"]
     [:ytunnus
      "8765432-1"]]]
   [:ilmoittaja
    [:henkilo
     [:etunimi
      "Urho"]
     [:sukunimi
      "Urakoitsija"]
     [:matkapuhelin
      "923458973"]
     [:sahkoposti
      "urho.urakoitsija@puulaaki.fi"]]
    [:organisaatio
     [:nimi
      "Puulaaki Oy"]
     [:ytunnus
      "1234567-8"]]]])

(defn muodosta [data]
  (let [sisalto (muodosta-viesti)
        xml (tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "harja-tloik.xsd" xml)
      xml
      (do
        (log/error "Ilmoitustoimenpidettä ei voida lähettää. XML ei ole validia.")
        nil))))
