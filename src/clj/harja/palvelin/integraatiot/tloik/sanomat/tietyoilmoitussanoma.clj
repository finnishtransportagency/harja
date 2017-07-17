(ns harja.palvelin.integraatiot.tloik.sanomat.tietyoilmoitussanoma
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.domain.tietyoilmoitukset :as tietyoilmoitus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.kyselyt.tietyoilmoitukset :as tietyoilmoitukset])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/tloik/")

(def data (tietyoilmoitukset/hae-ilmoitus (:db harja.palvelin.main/harja-jarjestelma) 1))

(defn muodosta-viesti [data viesti-id]
  (let [ilmoittaja (::tietyoilmoitus/ilmoittaja data)
        urakoitsijan-yhteyshenkilo (::tietyoilmoitus/urakoitsijan-yhteyshenkilo data)
        osoite (:osoite data)]

    [:harja:tietyoilmoitus
     {:xmlns:harja "http://www.liikennevirasto.fi/xsd/harja"}
     [:viestiId viesti-id]
     [:harja-tietyoilmoitus-id (::tietyoilmoitus/id data)]
     ;; todo: lisätään möyhemmin
     ;; [:tloik-tietyoilmoitus-id "234908234"]
     ;; todo: päättele
     [:toimenpide "uusi"]
     [:kirjattu (xml/datetime->gmt-0 (::muokkaustiedot/luotu data))]
     [:ilmoittaja
      [:etunimi (::tietyoilmoitus/etunimi ilmoittaja)]
      [:sukunimi (::tietyoilmoitus/sukunimi ilmoittaja)]
      [:matkapuhelin (::tietyoilmoitus/matkapuhelin ilmoittaja)]
      [:sahkoposti (::tietyoilmoitus/sahkoposti ilmoittaja)]]
     [:urakka
      [:id (::tietyoilmoitus/urakka-id data)]
      [:nimi (::tietyoilmoitus/urakka-nimi data)]
      [:tyyppi (::tietyoilmoitus/urakkatyyppi data)]]
     [:urakoitsija
      [:nimi (::tietyoilmoitus/urakoitsijan-nimi data)]
      ;; todo: lisättävä frontille ja kantaan
      [:ytunnus "2163026-3"]]
     [:urakoitsijan-yhteyshenkilot
      ;; todo: nämä pitäisi hakea FIM:stä, jos niitä ei ole suoraan kirjattu kantaan?
      [:urakoitsijan-yhteyshenkilo
       [:etunimi "Urho"]
       [:sukunimi "Urakoitsija"]
       [:matkapuhelin "+34592349342"]
       [:tyopuhelin "+34592349342"]
       [:sahkoposti "urho@example.com"]
       [:vastuuhenkilo "true"]]]
     [:tilaaja
      [:nimi (::tietyoilmoitus/tilaajan-nimi data)]]
     ;; todo: nämä pitäisi hakea FIM:stä, jos niitä ei ole suoraan kirjattu kantaan?
     [:tilaajan-yhteyshenkilot
      [:tilaajan-yhteyshenkilo
       [:etunimi "Eija"]
       [:sukunimi "Elyläinen"]
       [:matkapuhelin "+34592349342"]
       [:tyopuhelin "+34592349342"]
       [:sahkoposti "eija@example.com"]
       [:vastuuhenkilo "true"]]]
     (into [:tyotyypit]
           (map #(vector :tyotyyppi
                         [:tyyppi (::tietyoilmoitus/tyyppi %)]
                         [:kuvaus (::tietyoilmoitus/kuvaus %)])
                (::tietyoilmoitus/tyotyypit data)))
     ;; todo: lisättävä kantaan
     [:luvan-diaarinumero "09864321"]
     [:sijainti
      [:tierekisteriosoitevali
       [:tienumero (::tierekisteri/tie osoite)]
       [:alkuosa (::tierekisteri/alkuosa osoite)]
       [:alkuetaisyys (::tierekisteri/alkuetaisyys osoite)]
       [:loppuosa (::tierekisteri/loppuosa osoite)]
       [:loppuetaisyys (::tierekisteri/loppuetaisyys osoite)]
       ;; todo: hae erikseen kannasta
       [:karttapvm "2016-01-01"]]
      [:alkukoordinaatit
       [:x "512980.1296580813"]
       [:y "6908379.5465487875"]]
      [:loppukoordinaatit
       [:x "515497.44830392423"]
       [:y "6908053.366497267"]]
      [:pituus "1000.00"]
      [:tienNimi "Tie"]
      [:kunnat "Pieksämäki"]
      [:alkusijainninKuvaus "Kaakinmäessä"]
      [:loppusijainninKuvaus "Rummukanmäessä"]]
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
     [:lisatietoja "Lisätietoja"]]))

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
