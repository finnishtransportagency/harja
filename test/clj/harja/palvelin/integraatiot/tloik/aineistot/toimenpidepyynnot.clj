(ns harja.palvelin.integraatiot.tloik.aineistot.toimenpidepyynnot
  (:require [taoensso.timbre :as log]
            [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clj-time
             [core :as t]
             [format :as df]]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms-test :refer [feikki-jms]]
            [harja.palvelin.integraatiot.tloik.kasittely.ilmoitus :as ilmoitus]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma :as ilmoitussanoma]
            [clojure.string :as clj-str]
            [harja.kyselyt.konversio :as konv]
            [clojure.set :as set]
            [harja.palvelin.palvelut.urakat :as urakkapalvelu]))

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

