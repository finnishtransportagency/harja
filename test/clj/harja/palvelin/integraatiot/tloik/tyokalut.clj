(ns harja.palvelin.integraatiot.tloik.tyokalut
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms :refer [feikki-sonja]]
            [harja.palvelin.integraatiot.tloik.kasittely.ilmoitus :as ilmoitus]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma :as ilmoitussanoma]))

(def +xsd-polku+ "xsd/tloik/")
(def +tloik-ilmoitusviestijono+ "tloik-ilmoitusviestijono")
(def +tloik-ilmoituskuittausjono+ "tloik-ilmoituskuittausjono")
(def +testi-ilmoitus-sanoma+ "<ilmoitus>
    <ilmoitettu>2008-09-29T04:49:45</ilmoitettu>
    <ilmoittaja>
        <etunimi>Irmeli</etunimi>
        <matkapuhelin>0903228927</matkapuhelin>
        <sahkoposti>irmeli.ilmoittaja@foo.bar</sahkoposti>
        <sukunimi>Ilmoittaja</sukunimi>
        <tyopuhelin>0908772789</tyopuhelin>
        <tyyppi>muu</tyyppi>
    </ilmoittaja>
    <ilmoitusId>123456789</ilmoitusId>
    <ilmoitustyyppi>toimenpidepyynto</ilmoitustyyppi>
    <valitettu>2014-06-09T18:15:04+03:00</valitettu>
    <lahettaja>
        <etunimi>Lasse</etunimi>
        <matkapuhelin>0807228930</matkapuhelin>
        <sahkoposti>lasse.lahettaja@foo.bar</sahkoposti>
        <sukunimi>Lähettäjä</sukunimi>
        <tyopuhelin>9809377280</tyopuhelin>
    </lahettaja>
    <seliteet>
        <selite>auraustarve</selite>
        <selite>aurausvallitNakemaesteena</selite>
    </seliteet>
    <sijainti>
        <tienumero>4</tienumero>
        <x>452935.0</x>
        <y>7186873.0</y>
    </sijainti>
    <urakkatyyppi>hoito</urakkatyyppi>
    <vapaateksti>Vanhat vallit ovat liian korkeat ja uutta lunta on satanut reippaasti.</vapaateksti>
    <viestiId>10a24e56-d7d4-4b23-9776-2a5a12f254af</viestiId>
    <yhteydenottopyynto>true</yhteydenottopyynto>
    <vastaanottaja>
        <nimi>Urakoitsija Oy</nimi>
        <ytunnus>1234567-8</ytunnus>
    </vastaanottaja>
</ilmoitus>")

(defn tuo-ilmoitus []
  (let [ilmoitus (ilmoitussanoma/lue-viesti +testi-ilmoitus-sanoma+)]
    (ilmoitus/kasittele-ilmoitus (:db jarjestelma) ilmoitus)))

(defn tuo-paallystysilmoitus []
  (let [sanoma (clojure.string/replace +testi-ilmoitus-sanoma+
                                       "<urakkatyyppi>hoito</urakkatyyppi>"
                                       "<urakkatyyppi>paallystys</urakkatyyppi>")
        ilmoitus (ilmoitussanoma/lue-viesti sanoma)]
    (ilmoitus/kasittele-ilmoitus (:db jarjestelma) ilmoitus)))

(defn hae-ilmoitus []
  (q "select * from ilmoitus where ilmoitusid = 123456789;"))

(defn poista-ilmoitus []
  (u "delete from ilmoitus where ilmoitusid = 123456789;"))