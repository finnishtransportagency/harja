(ns harja.palvelin.integraatiot.tloik.ilmoitukset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms :refer [feikki-sonja]]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tloik.kasittely.ilmoitus :as ilmoitus]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma :as ilmoitussanoma]
            [harja.testi :as testi]
            [harja.palvelin.integraatiot.tloik.ilmoitukset :as ilmoitukset])
  (:import (javax.jms TextMessage)))

(def +xsd-polku+ "resources/xsd/sampo/inbound/")
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
        <x>41.40338</x>
        <y>2.17403</y>
    </sijainti>
    <urakkatyyppi>paallystys</urakkatyyppi>
    <vapaateksti>Vanhat vallit ovat liian korkeat ja uutta lunta on satanut reippaasti.</vapaateksti>
    <viestiId>10a24e56-d7d4-4b23-9776-2a5a12f254af</viestiId>
    <yhteydenottopyynto>true</yhteydenottopyynto>
    <vastaanottaja>
        <nimi>Urakoitsija Oy</nimi>
        <ytunnus>1234567-8</ytunnus>
    </vastaanottaja>
</ilmoitus>")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :sonja (feikki-sonja)
                        :integraatioloki (component/using (->Integraatioloki nil) [:db])
                        :tloik (component/using
                                 (->Tloik +tloik-ilmoitusviestijono+ +tloik-ilmoituskuittausjono+)
                                 [:db :sonja :integraatioloki])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(defn tuo-ilmoitus []
  (let [ilmoitus  (ilmoitussanoma/lue-viesti +testi-ilmoitus-sanoma+)]
    (ilmoitus/kasittele-ilmoitus (:db jarjestelma) ilmoitus)))

(defn hae-ilmoitus []
  (q "select * from ilmoitus where ilmoitusid = 123456789;"))

(defn poista-ilmoitus []
  (u "delete from ilmoitus where ilmoitusid = 123456789;"))

(deftest tarkista-uuden-ilmoituksen-tallennus
  (tuo-ilmoitus)
  (is (= 1 (count (hae-ilmoitus))) "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä.")
  (poista-ilmoitus))

(deftest tarkista-ilmoituksen-paivitys
  (tuo-ilmoitus)
  (is (= 1 (count (hae-ilmoitus))) "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä.")
  (tuo-ilmoitus)
  (is (= 1 (count (hae-ilmoitus))) "Kun viesti on tuotu toiseen kertaan, on päivitetty olemassa olevaa ilmoitusta eikä luotu uutta.")
  #_(poista-ilmoitus))

#_(deftest tarkista-viestin-kasittely-ja-kuittaukset
  (let [viestit (atom [])]
    (sonja/kuuntele (:sonja jarjestelma) +tloik-ilmoituskuittausjono+ #(swap! viestit conj (.getText %)))
    (sonja/laheta (:sonja jarjestelma) +tloik-ilmoituskuittausjono+ +testi-ilmoitus-sanoma+)

    (odota #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." 10000)
    ;;todo: toteuta kuittausten tarkistus
    #_(let [xml (first @viestit)
          data (xml/lue xml)]
      (is (xml/validoi +xsd-polku+ "HarjaToSampoAcknowledgement.xsd" xml) "Kuittaus on validia XML:ää.")

      (is (= "UrakkaMessageId" (first (z/xml-> data (fn [kuittaus] (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :MessageId))))))
          "Kuittaus on tehty oikeaan viestiin.")

      (is (= "Project" (first (z/xml-> data (fn [kuittaus] (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :ObjectType))))))
          "Kuittauksen tyyppi on Project eli urakka.")

      (is (= "NA" (first (z/xml-> data (fn [kuittaus] (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :ErrorCode))))))
          "Virheitä ei tapahtunut käsittelyssä.")))

  (is (= 1 (count (hae-ilmoitus))) "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä")
  (poista-ilmoitus))


