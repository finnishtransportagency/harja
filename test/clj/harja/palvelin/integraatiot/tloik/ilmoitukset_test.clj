(ns ^:integraatio harja.palvelin.integraatiot.tloik.ilmoitukset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [harja.integraatio :as integraatio]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.palvelin.komponentit.sonja :as sonja]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.labyrintti.sms :refer [->Labyrintti]]
            [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]
            [harja.palvelin.integraatiot.sonja.tyokalut :as s-tk]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.pvm :as pvm]
            [clj-time
             [coerce :as tc]
             [format :as df]]
            [clojure.core.async :as async])
  (:import (org.postgis PGgeometry)))

(def kayttaja "yit-rakennus")

(defonce asetukset {:sonja integraatio/sonja-asetukset})

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-ilmoitukset (component/using
                       (api-ilmoitukset/->Ilmoitukset)
                       [:http-palvelin :db :integraatioloki])
    :sonja (component/using
             (sonja/luo-oikea-sonja (:sonja asetukset))
             [:db])
    :sonja-sahkoposti (component/using
                        (sahkoposti/luo-sahkoposti "foo@example.com"
                                                   {:sahkoposti-sisaan-jono "email-to-harja"
                                                    :sahkoposti-ulos-jono "harja-to-email"
                                                    :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                        [:sonja :db :integraatioloki])
    :labyrintti (component/using
                  (labyrintti/->Labyrintti "foo" "testi" "testi" (atom #{}))
                  [:db :http-palvelin :integraatioloki])
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :sonja :integraatioloki :labyrintti :sonja-sahkoposti])))

(use-fixtures :each (fn [testit]
                      (binding [*aloita-sonja?* true
                                *lisattavia-kuuntelijoita?* true
                                *sonja-kaynnistetty-fn* (fn []
                                                          (s-tk/sonja-jolokia-jono +tloik-ilmoitusviestijono+ nil :purge)
                                                          (s-tk/sonja-jolokia-jono +tloik-ilmoituskuittausjono+ nil :purge))]
                        (jarjestelma-fixture testit))))

(deftest tarkista-uuden-ilmoituksen-tallennus
  (ei-lisattavia-kuuntelijoita!)
  (tuo-ilmoitus)
  (let [ilmoitukset (hae-testi-ilmoitukset)
        ilmoitus (first ilmoitukset)
        urakka-id (hae-rovaniemen-maanteiden-hoitourakan-id)]
    (is (= 1 (count ilmoitukset)) "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä.")
    (is (= (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss")
                       (tc/from-date (:ilmoitettu ilmoitus)))
           ilmoitettu))
    (is (= (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss")
                       (tc/from-date (:valitetty ilmoitus)))
           valitetty))
    (is (= (:yhteydenottopyynto ilmoitus) false))
    (is (= (:tila ilmoitus) "kuittaamaton"))
    (is (= (:tunniste ilmoitus) "UV-1509-1a"))
    (is (= (:ilmoittaja_tyyppi ilmoitus) "tienkayttaja"))
    (is (instance? PGgeometry (:sijainti ilmoitus)))
    (is (= (:ilmoittaja_matkapuhelin ilmoitus) "08023394852"))
    (is (= (:ilmoitus-id ilmoitus) 123456789))
    (is (= (:ilmoittaja_etunimi ilmoitus) "Uuno"))
    (is (= (:ilmoittaja_sukunimi ilmoitus) "Urakoitsija"))
    (is (= (:ilmoitustyyppi ilmoitus) "toimenpidepyynto"))
    (is (= (:ilmoittaja_sahkoposti ilmoitus) "uuno.urakoitsija@example.com"))
    (is (= (:urakka ilmoitus) urakka-id))
    (is (= (:tr_numero ilmoitus) 79))
    (is (= (:lahettaja_etunimi ilmoitus) "Pekka"))
    (is (= (:lahettaja_sukunimi ilmoitus) "Päivystäjä"))
    (is (= (:lahettaja_sahkoposti ilmoitus) "pekka.paivystaja@livi.fi"))
    (is (= (:lisatieto ilmoitus) "Vanhat vallit ovat liian korkeat ja uutta lunta on satanut reippaasti."))
    (is (= #{"auraustarve"
             "aurausvallitNakemaesteena"} (:selitteet ilmoitus))))
  (poista-ilmoitus))

(deftest tarkista-ilmoituksen-paivitys
  (ei-lisattavia-kuuntelijoita!)
  (tuo-ilmoitus)
  (is (= 1 (count (hae-testi-ilmoitukset)))
      "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä.")
  (tuo-ilmoitus)
  (is (= 1 (count (hae-testi-ilmoitukset)))
      "Kun viesti on tuotu toiseen kertaan, on päivitetty olemassa olevaa ilmoitusta eikä luotu uutta.")
  (poista-ilmoitus))

(deftest tarkista-ilmoituksen-urakan-paattely
  (ei-lisattavia-kuuntelijoita!)
  (tuo-ilmoitus)
  (is (= (first (q "select id from urakka where nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu tyypin ja sijainnin mukaan oikein käynnissäolevaksi Oulun alueurakaksi 2014-2019.")
  (poista-ilmoitus)

  (tuo-paallystysilmoitus)
  (is (= (first (q "select id from urakka where nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu oletuksena hoidon alueurakalle, kun sijainnissa ei ole käynnissä päällystysurakkaa.")
  (poista-ilmoitus)

  (tuo-ilmoitus-teknisista-laitteista)
  (is (= (first (q "select id from urakka where nimi = 'PIR RATU IHJU';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu oikein tekniset laitteet urakalle.")
  (poista-ilmoitus)

  (tuo-ilmoitus-siltapalvelusopimukselle)
  (is (= (first (q "select id from urakka where nimi = 'KAS siltojen ylläpidon palvelusopimus Etelä-Karjala';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu oikein siltojen palvelusopimukselle.")

  (poista-ilmoitus))

(deftest tarkista-viestin-kasittely-ja-kuittaukset
  "Tarkistaa että ilmoituksen saapuessa data on käsitelty oikein, että ilmoituksia API:n kautta kuuntelevat tahot saavat
   viestit ja että kuittaukset on välitetty oikein Tieliikennekeskukseen"
  (let [viestit (atom [])]
    (lisaa-kuuntelijoita! {+tloik-ilmoituskuittausjono+ #(swap! viestit conj (.getText %))})

    ;; Ilmoitushausta tehdään future, jotta HTTP long poll on jo käynnissä, kun uusi ilmoitus vastaanotetaan
    (let [urakka-id (hae-rovaniemen-maanteiden-hoitourakan-id)
          ilmoitushaku (future (api-tyokalut/get-kutsu ["/api/urakat/" urakka-id "/ilmoitukset?odotaUusia=true"]
                                                       kayttaja portti))]
      (async/<!! (async/timeout 1000))
      (sonja/laheta (:sonja jarjestelma) +tloik-ilmoitusviestijono+ (testi-ilmoitus-sanoma))

      (odota-ehdon-tayttymista #(realized? ilmoitushaku) "Saatiin vastaus ilmoitushakuun." 20000)
      (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." 20000)

      (let [xml (first @viestit)
            data (xml/lue xml)]
        (is (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" xml) "Kuittaus on validia XML:ää.")
        (is (= "10a24e56-d7d4-4b23-9776-2a5a12f254af" (z/xml1-> data :viestiId z/text))
            "Kuittauksen on tehty oikeaan viestiin.")
        (is (= "valitetty" (z/xml1-> data :kuittaustyyppi z/text)) "Kuittauksen tyyppi on oikea.")
        (is (empty? (z/xml1-> data :virhe z/text)) "Virheitä ei ole raportoitu."))

      (is (= 1 (count (hae-testi-ilmoitukset)))
          "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä")

      (let [{:keys [status body] :as vastaus} @ilmoitushaku]
        (println "ilmoitushaku: " vastaus)
        (is (= 200 status) "Ilmoituksen haku APIsta onnistuu")
        (is (= (-> (cheshire/decode body)
                   (get "ilmoitukset")
                   count) 1) "Ilmoituksia on vastauksessa yksi")))
    (poista-ilmoitus)))

(deftest tarkista-viestin-kasittely-kun-urakkaa-ei-loydy
  (let [sanoma +ilmoitus-ruotsissa+
        viestit (atom [])]
    (lisaa-kuuntelijoita! {+tloik-ilmoituskuittausjono+ #(swap! viestit conj (.getText %))})
    (sonja/laheta (:sonja jarjestelma) +tloik-ilmoitusviestijono+ sanoma)

    (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." 10000)

    (let [xml (first @viestit)
          data (xml/lue xml)]
      (is (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" xml) "Kuittaus on validia XML:ää.")
      (is (= "10a24e56-d7d4-4b23-9776-2a5a12f254af" (z/xml1-> data :viestiId z/text))
          "Kuittauksen on tehty oikeaan viestiin.")
      (is (= "virhe" (z/xml1-> data :kuittaustyyppi z/text)) "Kuittauksen tyyppi on oikea.")
      (is (= "Tiedoilla ei voitu päätellä urakkaa." (z/xml1-> data :virhe z/text))
          "Virheitä ei ole raportoitu."))

    (is (= 0 (count (hae-testi-ilmoitukset))) "Tietokannasta ei löydy ilmoitusta T-LOIK:n id:llä")
    (poista-ilmoitus)))

(deftest ilmoittaja-kuuluu-urakoitsijan-organisaatioon-merkitaan-vastaanotetuksi
  (try
    (let [kuittausviestit (atom [])]
      (lisaa-kuuntelijoita! {+tloik-ilmoituskuittausjono+ #(swap! kuittausviestit conj (.getText %))})

      (sonja/laheta (:sonja jarjestelma)
                    +tloik-ilmoitusviestijono+
                    (testi-ilmoitus-sanoma-jossa-ilmoittaja-urakoitsija))

      (odota-ehdon-tayttymista #(= 1 (count @kuittausviestit)) "Kuittaus ilmoitukseen vastaanotettu." 10000)

      (is (= 1 (count (hae-ilmoitustoimenpide))) "Viestille löytyy ilmoitustoimenpide")
      (is (= (ffirst (hae-ilmoitustoimenpide)) "vastaanotto") "Viesti on käsitelty ja merkitty vastaanotetuksi")

      (poista-ilmoitus))
    (catch IllegalArgumentException e
      (is false "Lähetystä Labyrintin SMS-Gatewayhyn ei yritetty."))))


(deftest tarkista-ilmoituksen-lahettaminen-valaistusurakalle
  "Tarkistaa että ilmoitus ohjataan oikein valaistusurakalle"
  (ei-lisattavia-kuuntelijoita!)
  (tuo-valaistusilmoitus)
  (is (= (first (q "select id from urakka where nimi = 'Oulun valaistuksen palvelusopimus 2013-2050';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 987654321;")))
      "Urakka on asetettu oletuksena hoidon alueurakalle, kun sijainnissa ei ole käynnissä päällystysurakkaa.")
  (poista-valaistusilmoitus))

(deftest tarkista-urakan-paattely-kun-alueella-ei-hoidon-urakkaa
  "Tarkistaa että ilmoitukselle saadaan pääteltyä urakka, kun ilmoitus on 10 km säteellä lähimmästä alueurakasta"
  (let [sanoma +ilmoitus-hailuodon-jaatiella+
        viestit (atom [])]
    (lisaa-kuuntelijoita! {+tloik-ilmoituskuittausjono+ #(swap! viestit conj (.getText %))})
    (sonja/laheta (:sonja jarjestelma) +tloik-ilmoitusviestijono+ sanoma)

    (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." 10000)

    (is (= (first (q "select id from urakka where nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)';"))
           (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
        "Urakka on asetettu tyypin ja sijainnin mukaan oikein käynnissäolevaksi Oulun alueurakaksi 2014-2019.")
    (poista-ilmoitus)))

(deftest tarkista-uusi-ilmoitus-ilman-tienumeroa
  (ei-lisattavia-kuuntelijoita!)
  (tuo-ilmoitus-ilman-tienumeroa)
  (let [ilmoitukset (hae-testi-ilmoitukset)
        ilmoitus (first ilmoitukset)]
    (is (= 1 (count ilmoitukset)) "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä.")
    (is (= (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss")
                       (tc/from-date (:ilmoitettu ilmoitus)))
           ilmoitettu)
        "Ilmoitusaika on parsittu oikein")
    (is (= (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss")
                       (tc/from-date (:valitetty ilmoitus)))
           valitetty)
        "Lähetysaika on parsittu oikein"))
  (poista-ilmoitus))
