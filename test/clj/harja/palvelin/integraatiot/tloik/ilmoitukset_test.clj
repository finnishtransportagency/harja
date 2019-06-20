(ns harja.palvelin.integraatiot.tloik.ilmoitukset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
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
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.pvm :as pvm])
  (:import (org.postgis PGgeometry)))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-ilmoitukset (component/using
                       (api-ilmoitukset/->Ilmoitukset)
                       [:http-palvelin :db :integraatioloki :klusterin-tapahtumat])
    :sonja (feikki-sonja)
    :sonja-sahkoposti (component/using
                        (sahkoposti/luo-sahkoposti "foo@example.com"
                                                   {:sahkoposti-sisaan-jono "email-to-harja"
                                                    :sahkoposti-ulos-jono "harja-to-email"
                                                    :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                        [:sonja :db :integraatioloki])
    :labyrintti (component/using
                  (labyrintti/luo-labyrintti
                    {:url "http://localhost:28080/sendsms"
                     :kayttajatunnus "solita-2" :salasana "ne8aCrasesev"})
                  [:db :http-palvelin :integraatioloki])
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :sonja :integraatioloki :klusterin-tapahtumat :labyrintti])))

(use-fixtures :each jarjestelma-fixture)

(deftest tarkista-uuden-ilmoituksen-tallennus
  (tuo-ilmoitus)
  (let [ilmoitukset (hae-testi-ilmoitukset)
        ilmoitus (first ilmoitukset)]
    (def ilmoitusasdf ilmoitus)
    (is (= 1 (count ilmoitukset)) "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä.")
    (is (= (pvm/pvm-aika (:ilmoitettu ilmoitus)) "29.9.2015 17:49"))
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
    (is (= (:urakka ilmoitus) 4))
    (is (= (:tr_numero ilmoitus) 4))
    (is (= (:lahettaja_etunimi ilmoitus) "Pekka"))
    (is (= (:lahettaja_sukunimi ilmoitus) "Päivystäjä"))
    (is (= (:lahettaja_sahkoposti ilmoitus) "pekka.paivystaja@livi.fi"))
    (is (= (:lisatieto ilmoitus) "Vanhat vallit ovat liian korkeat ja uutta lunta on satanut reippaasti."))
    (is (= #{"auraustarve"
             "aurausvallitNakemaesteena"} (:selitteet ilmoitus))))
  (poista-ilmoitus))

(deftest tarkista-ilmoituksen-paivitys
  (tuo-ilmoitus)
  (is (= 1 (count (hae-testi-ilmoitukset)))
      "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä.")
  (tuo-ilmoitus)
  (is (= 1 (count (hae-testi-ilmoitukset)))
      "Kun viesti on tuotu toiseen kertaan, on päivitetty olemassa olevaa ilmoitusta eikä luotu uutta.")
  (poista-ilmoitus))

(deftest tarkista-ilmoituksen-urakan-paattely
  (tuo-ilmoitus)
  (is (= (first (q "select id from urakka where nimi = 'Oulun alueurakka 2014-2019';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu tyypin ja sijainnin mukaan oikein käynnissäolevaksi Oulun alueurakaksi 2014-2019.")
  (poista-ilmoitus)

  (tuo-paallystysilmoitus)
  (is (= (first (q "select id from urakka where nimi = 'Oulun alueurakka 2014-2019';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu oletuksena hoidon alueurakalle, kun sijainnissa ei ole käynnissä päällystysurakkaa.")
  (poista-ilmoitus)

  (tuo-ilmoitus-teknisista-laitteista)
  (is (= (first (q "select id from urakka where nimi = 'PIR RATU IHJU 2016 -2022, P';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu oikein tekniset laitteet urakalle.")
  (poista-ilmoitus)

  (tuo-ilmoitus-siltapalvelusopimukselle)
  (is (= (first (q "select id from urakka where nimi = 'KAS siltojen ylläpidon palvelusopimus Etelä-Karjala 2016-2019, P';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu oikein siltojen palvelusopimukselle.")

  (poista-ilmoitus))

(deftest tarkista-viestin-kasittely-ja-kuittaukset
  "Tarkistaa että ilmoituksen saapuessa data on käsitelty oikein, että ilmoituksia API:n kautta kuuntelevat tahot saavat
   viestit ja että kuittaukset on välitetty oikein Tieliikennekeskukseen"
  (let [viestit (atom [])]
    (sonja/kuuntele! (:sonja jarjestelma) +tloik-ilmoituskuittausjono+
                    #(swap! viestit conj (.getText %)))

    ;; Ilmoitushausta tehdään future, jotta HTTP long poll on jo käynnissä, kun uusi ilmoitus vastaanotetaan
    (let [ilmoitushaku (future (api-tyokalut/get-kutsu ["/api/urakat/4/ilmoitukset?odotaUusia=true"]
                                                       kayttaja portti))]
      (sonja/laheta (:sonja jarjestelma) +tloik-ilmoitusviestijono+ +testi-ilmoitus-sanoma+)

      (odota-ehdon-tayttymista #(realized? ilmoitushaku) "Saatiin vastaus ilmoitushakuun." 10000)
      (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." 100000)

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
    (sonja/kuuntele! (:sonja jarjestelma) +tloik-ilmoituskuittausjono+
                    #(swap! viestit conj (.getText %)))
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
    (with-fake-http []
      (let [kuittausviestit (atom [])]
        (sonja/kuuntele! (:sonja jarjestelma) +tloik-ilmoituskuittausjono+
                        #(swap! kuittausviestit conj (.getText %)))

        (sonja/laheta (:sonja jarjestelma)
                      +tloik-ilmoitusviestijono+
                      +testi-ilmoitus-sanoma-jossa-ilmoittaja-urakoitsija+)

        (odota-ehdon-tayttymista #(= 1 (count @kuittausviestit)) "Kuittaus ilmoitukseen vastaanotettu." 10000)

        (is (= 1 (count (hae-ilmoitustoimenpide))) "Viestille löytyy ilmoitustoimenpide")
        (is (= (ffirst (hae-ilmoitustoimenpide)) "vastaanotto") "Viesti on käsitelty ja merkitty vastaanotetuksi")

        (poista-ilmoitus)))
    (catch IllegalArgumentException e
      (is false "Lähetystä Labyrintin SMS-Gatewayhyn ei yritetty."))))


(deftest tarkista-ilmoituksen-lahettaminen-valaistusurakalle
  "Tarkistaa että ilmoitus ohjataan oikein valaistusurakalle"
  (tuo-valaistusilmoitus)
  (is (= (first (q "select id from urakka where nimi = 'Oulun valaistuksen palvelusopimus 2013-2050';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 987654321;")))
      "Urakka on asetettu oletuksena hoidon alueurakalle, kun sijainnissa ei ole käynnissä päällystysurakkaa.")
  (poista-valaistusilmoitus))

(deftest tarkista-urakan-paattely-kun-alueella-ei-hoidon-urakkaa
  "Tarkistaa että ilmoitukselle saadaan pääteltyä urakka, kun ilmoitus on 10 km säteellä lähimmästä alueurakasta"
  (let [sanoma +ilmoitus-hailuodon-jaatiella+
        viestit (atom [])]
    (sonja/kuuntele! (:sonja jarjestelma) +tloik-ilmoituskuittausjono+
                    #(swap! viestit conj (.getText %)))
    (sonja/laheta (:sonja jarjestelma) +tloik-ilmoitusviestijono+ sanoma)

    (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." 10000)

    (is (= (first (q "select id from urakka where nimi = 'Oulun alueurakka 2014-2019';"))
           (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
        "Urakka on asetettu tyypin ja sijainnin mukaan oikein käynnissäolevaksi Oulun alueurakaksi 2014-2019.")
    (poista-ilmoitus)))

(deftest tarkista-uusi-ilmoitus-ilman-tienumeroa
  (tuo-ilmoitus-ilman-tienumeroa)
  (let [ilmoitukset (hae-testi-ilmoitukset)
        ilmoitus (first ilmoitukset)]
    (is (= 1 (count ilmoitukset)) "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä.")
    (is (is (= (pvm/pvm-aika (:ilmoitettu ilmoitus)) "29.9.2015 17:49")) "Ilmoitusaika on parsittu oikein"))
  (poista-ilmoitus))
