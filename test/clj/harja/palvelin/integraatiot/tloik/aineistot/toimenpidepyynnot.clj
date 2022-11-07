(ns harja.palvelin.integraatiot.tloik.aineistot.toimenpidepyynnot
  (:require [clj-time
             [core :as t]
             [format :as df]]
            [harja.testi :refer :all]))

;; Kellonajat käsitellään ilmoituksissa utf ajassa, joten Helsingin aika voi olla kolme tuntia tai kaksi tuntia
;; sitä aikaa myöhemmin. Niinpä siirretään varalta tässä annetut ajat vähintään kolmen tunnin päähän, että timezoneton
;; ilmoituskäsittelijä ymmärtää käsitellä ajat oikein
(def ilmoitettu (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
                  (t/minus (t/now) (t/minutes 205))))
(def valitetty (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
                 (t/minus (t/now) (t/minutes 200))))

(def sijainti-oulun-alueella
  "<sijainti>
    <tienumero>815</tienumero>
    <x>439082.6599999999743886291980743408203125</x>
    <y>7220843.320000000298023223876953125</y>
   </sijainti>")

(def ilmoittaja-ilman-puhelinta-xml
  "<ilmoittaja>
    <tyyppi>tienkayttaja</tyyppi>
  </ilmoittaja>")

(def ilmoittaja-xml
  "<ilmoittaja>
    <matkapuhelin>99999999</matkapuhelin>
    <tyyppi>tienkayttaja</tyyppi>
  </ilmoittaja>")


(defn toimenpidepyynto-sanoma [viesti-id ilmoitus-id sijainti ilmoittaja]
  (str
    "<harja:ilmoitus xmlns:harja=\"http://www.liikennevirasto.fi/xsd/harja\">
        <viestiId>" viesti-id "</viestiId>
     <lahetysaika>" valitetty "</lahetysaika>
     <ilmoitusId>" ilmoitus-id "</ilmoitusId>
     <tunniste>OlenTunniste</tunniste>
     <versionumero>0</versionumero>
     <ilmoitustyyppi>toimenpidepyynto</ilmoitustyyppi>
     <ilmoitettu>" ilmoitettu "</ilmoitettu>
     <urakkatyyppi>hoito</urakkatyyppi>
     <otsikko>Urakoitsijaviesti</otsikko>
     <paikanKuvaus>Tämä tarkentaa sijaintia todella hirmuisesti!</paikanKuvaus>
     <yhteydenottopyynto>true</yhteydenottopyynto>
     " sijainti "
     " ilmoittaja "
     <lahettaja>
      <etunimi>Lahettaja</etunimi>
      <sukunimi>Lahtinen</sukunimi>
      <matkapuhelin>99999999</matkapuhelin>
      <sahkoposti>testi.emailtesti@example.fi</sahkoposti>
     </lahettaja>
    <seliteet>
      <selite>auraustarve</selite>
    </seliteet>
  </harja:ilmoitus>
  "))

(defn toimenpidepyynto-ilmoittaja-sanoma [{:keys [viesti-id ilmoitus-id sijainti-xml
                                                  ilmoittaja-etunimi ilmoittaja-sukunimi ilmoittaja-email ilmoittaja-tyyppi]}]
  (str
    "<ns0:ilmoitus xmlns:ns0=\"http://www.liikennevirasto.fi/xsd/harja\">
      <viestiId>"viesti-id"</viestiId>
      <lahetysaika>2022-11-04T07:24:43.000Z</lahetysaika>
      <ilmoitusId>"ilmoitus-id"</ilmoitusId>
      <tunniste>UV-2244-li85</tunniste>
      <versionumero>0</versionumero>
      <ilmoitustyyppi>tiedoitus</ilmoitustyyppi>
      <ilmoitettu>2022-11-04T07:24:43.000Z</ilmoitettu>
      <urakkatyyppi>hoito</urakkatyyppi>
      <otsikko>Urakoitsijaviesti</otsikko>
      <paikanKuvaus>Pohjalantien tasoristeys</paikanKuvaus>
      <lisatieto>Aihe: Soratien kunto
        Lisätieto: Soratie on kuoppainen tai epätasainen
        Otsikko: Pohjalantie.
        Kuvaus: Sastamala, Pohjalantie, pahasti kuopilla loppupäästä, ei voi kiertää. Auton renkaat ei kestä, joka päivä
        ajaa.
      </lisatieto>
      <yhteydenottopyynto>false</yhteydenottopyynto>
      "sijainti-xml"
      <ilmoittaja>
        <etunimi>"ilmoittaja-etunimi"</etunimi>
        <sukunimi>"ilmoittaja-sukunimi"</sukunimi>
        <matkapuhelin></matkapuhelin>
        <sahkoposti>"ilmoittaja-email"</sahkoposti>
        <tyyppi>"ilmoittaja-tyyppi"</tyyppi>
      </ilmoittaja>
      <lahettaja>
        <etunimi>Tiina</etunimi>
        <sukunimi>Korteniitty</sukunimi>
        <matkapuhelin>0295020600</matkapuhelin>
        <sahkoposti>tiina.korteniitty@ely-keskus.fi</sahkoposti>
      </lahettaja>
      <seliteet>
        <selite>soratienKuntoHuono</selite>
      </seliteet>
      </ns0:ilmoitus>"))

