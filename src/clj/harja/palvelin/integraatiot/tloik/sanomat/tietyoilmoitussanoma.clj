(ns harja.palvelin.integraatiot.tloik.sanomat.tietyoilmoitussanoma
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.tyokalut.merkkijono :as merkkijono])
  (:use [slingshot.slingshot :only [throw+]])
  (:import (java.text SimpleDateFormat)
           (java.util TimeZone)))

(def +xsd-polku+ "xsd/tloik/")

(defn muodosta-viesti [data viesti-id]
  [:harja:tietyoilmoitus
   {:xmlns:harja "http://www.liikennevirasto.fi/xsd/harja"}
   [:viestiid viesti-id]
   [:harja-tietyoilmoitus-id "234908234"]
   [:tloik-tietyoilmoitus-id "234908234"]
   [:toimenpide "uusi"]
   [:kirjattu "2016-12-24T04:49:45"]
   [:ilmoittaja
    [:etunimi "Irma"]
    [:sukunimi "Ilmoittaja"]
    [:matkapuhelin "+34592349342"]
    [:tyopuhelin "+34592349342"]
    [:sahkoposti "irma@example.com"]]
   [:urakka
    [:id "15"]
    [:nimi "Pieksämäki alueurakka 2013-2014, P"]
    [:tyyppi "hoito"]]
   [:urakoitsija
    [:nimi "Destia Oy"]
    [:ytunnus "2163026-3"]]
   [:urakoitsijan-yhteyshenkilot
    [:urakoitsijan-yhteyshenkilo
     [:etunimi "Urho"]
     [:sukunimi "Urakoitsija"]
     [:matkapuhelin "+34592349342"]
     [:tyopuhelin "+34592349342"]
     [:sahkoposti "urho@example.com"]
     [:vastuuhenkilo "true"]]]
   [:tilaaja
    [:nimi "Pohjois-Savo"]]
   [:tilaajan-yhteyshenkilot
    [:tilaajan-yhteyshenkilo
     [:etunimi "Eija"]
     [:sukunimi "Elyläinen"]
     [:matkapuhelin "+34592349342"]
     [:tyopuhelin "+34592349342"]
     [:sahkoposti "eija@example.com"]
     [:vastuuhenkilo "true"]]]
   [:tyyppi
    [:tyotyypit
     [:tyotyyppi "Viimeistely"]
     [:tyotyyppi "Viimeistely"]]
    [:tyypinkuvaus "Viimeistellään töitä"]]
   [:luvan-diaarinumero "09864321"]
   [:sijainti
    [:tierekisteriosoitevali
     [:tienumero "23"]
     [:alkuosa "313"]
     [:alkuetaisyys "1216"]
     [:loppuosa "313"]
     [:loppuetaisyys "3780"]
     [:karttapvm "2016-01-01"]]
    [:alkukoordinaatit
     [:x "512980.1296580813"]
     [:y "6908379.5465487875"]]
    [:loppukoordinaatit
     [:x "515497.44830392423"]
     [:y "6908053.366497267"]]
    [:pituus "1000.00"]
    [:tiennimi "Tie"]
    [:kunnat "Pieksämäki"]
    [:alkusijainninkuvaus "Kaakinmäessä"]
    [:loppusijainninkuvaus "Rummukanmäessä"]]
   [:ajankohta
    [:alku "2016-01-01T07:49:45+02:00"]
    [:loppu "2017-01-15T18:41:13+02:00"]]
   [:tyoajat
    [:tyoaika
     [:alku "07:00:00+02:00"]
     [:loppu "18:00:00+02:00"]
     [:paivat
      [:paiva "maanantai"]
      [:paiva "tiistai"]
      [:paiva "keskiviikko"]
      [:paiva "torstai"]
      [:paiva "perjantai"]]]]
   [:vaikutukset
    [:vaikutussuunta "molemmat"]
    [:kaistajarjestelyt "ajorataSuljettu"]
    [:nopeusrajoitukset
     [:nopeusrajoitus
      [:rajoitus "40"]
      [:matka "100"]]]
    [:tienpinnat
     [:tienpinta
      [:pintamateriaali "paallystetty"]
      [:matka "100"]]]
    [:kiertotie
     [:mutkaisuus "loivatMutkat"]
     [:tienpinnat
      [:tienpinta
       [:pintamateriaali "paallystetty"]
       [:matka "100"]]]]
    [:liikenteenohjaus
     [:ohjaus "ohjataanVuorotellen"]
     [:ohjaaja "liikenteenohjaaja"]]
    [:arvioitu-viivastys
     [:normaali-liikenteessa "100"]
     [:ruuhka-aikana "100"]]
    [:ajoneuvorajoitukset
     [:max-korkeus "100"]
     [:max-leveys "100"]
     [:max-pituus "100"]
     [:max-paino "100"]]
    [:huomautukset
     [:huomautus "avotuli"]]
    [:pysaytykset
     [:pysaytetaan-ajoittain "true"]
     [:tie-ajoittain-suljettu "false"]
     [:aikataulu
      [:alkaen "2017-04-19T17:38:57+03:00"]
      [:paattyen "2008-08-06T17:17:00+03:00"]]]]
   [:lisatietoja "Lisätietoja"]])

(defn muodosta [data viesti-id]
  (let [sisalto (muodosta-viesti data viesti-id)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" xml)
      xml
      (let [virheviesti (format "Ilmoitustoimenpidettä ei voida lähettää. XML ei ole validia. XML: %s." xml)]
        (log/error virheviesti)
        (throw+ {:type virheet/+invalidi-xml+
                 :virheet [{:koodi :invalidi-ilmoitustoimenpide-xml
                            :viesti virheviesti}]})))))
