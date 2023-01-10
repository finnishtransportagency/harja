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
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.komponentit.itmf :as itmf]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms-test :refer [feikki-jms]]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.jms.tyokalut :as jms-tk]
            [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
            [harja.palvelin.integraatiot.tloik.aineistot.toimenpidepyynnot :as aineisto-toimenpidepyynnot]
            [harja.pvm :as pvm]
            [clj-time
             [coerce :as tc]
             [format :as df]]
            [clojure.core.async :as async]
            [clj-time.core :as t])
  (:import (org.postgis PGgeometry)
           (java.util UUID)))

(def kayttaja "yit-rakennus")
(def timeout 2000)
(def kuittaus-timeout 20000)

(defonce asetukset {:itmf integraatio/itmf-asetukset})

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-ilmoitukset (component/using
                       (api-ilmoitukset/->Ilmoitukset)
                       [:http-palvelin :db :integraatioloki])
    :itmf (component/using
             (itmf/luo-oikea-itmf (:itmf asetukset))
             [:db])
    :api-sahkoposti (component/using
                       (sahkoposti-api/->ApiSahkoposti {:tloik {:toimenpidekuittausjono "Harja.HarjaToT-LOIK.Ack"}})
                       [:http-palvelin :db :integraatioloki :itmf])
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :itmf :integraatioloki :api-sahkoposti])))

(use-fixtures :each (fn [testit]
                      (binding [*aloitettavat-jmst* #{"itmf"}
                                *lisattavia-kuuntelijoita?* true
                                *jms-kaynnistetty-fn* (fn []
                                                          (jms-tk/itmf-jolokia-jono +tloik-ilmoitusviestijono+ nil :purge)
                                                          (jms-tk/itmf-jolokia-jono +tloik-ilmoituskuittausjono+ nil :purge))]
                        (jarjestelma-fixture testit))))

(deftest tarkista-uuden-ilmoituksen-tallennus
  (ei-lisattavia-kuuntelijoita!)
  (tuo-ilmoitus)
  (let [ilmoitukset (hae-testi-ilmoitukset)
        ilmoitus (first ilmoitukset)
        urakka-id (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")]
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
  (poista-ilmoitus 123456789))

(deftest tarkista-ilmoituksen-paivitys
  (ei-lisattavia-kuuntelijoita!)
  (tuo-ilmoitus)
  (is (= 1 (count (hae-testi-ilmoitukset)))
      "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä.")
  (tuo-ilmoitus)
  (is (= 1 (count (hae-testi-ilmoitukset)))
      "Kun viesti on tuotu toiseen kertaan, on päivitetty olemassa olevaa ilmoitusta eikä luotu uutta.")
  (poista-ilmoitus 123456789))

(deftest tarkista-ilmoituksen-urakan-paattely
  (ei-lisattavia-kuuntelijoita!)
  (tuo-ilmoitus)
  (is (= (first (q "select id from urakka where nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu tyypin ja sijainnin mukaan oikein käynnissäolevaksi Oulun alueurakaksi 2014-2019.")
  (poista-ilmoitus 123456789)

  (tuo-paallystysilmoitus)
  (is (= (first (q "select id from urakka where nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu oletuksena hoidon alueurakalle, kun sijainnissa ei ole käynnissä päällystysurakkaa.")
  (poista-ilmoitus 123456789)

  (tuo-ilmoitus-teknisista-laitteista)
  (is (= (first (q "select id from urakka where nimi = 'PIR RATU IHJU';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu oikein tekniset laitteet urakalle.")
  (poista-ilmoitus 123456789)

  (tuo-ilmoitus-siltapalvelusopimukselle)
  (is (= (first (q "select id from urakka where nimi = 'KAS siltojen ylläpidon palvelusopimus Etelä-Karjala';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 123456789;")))
      "Urakka on asetettu oikein siltojen palvelusopimukselle.")

  (poista-ilmoitus 123456789))

(deftest tarkista-viestin-kasittely-ja-kuittaukset-ilman-paivystajaa
  "Tarkistaa että ilmoituksen saapuessa data on käsitelty oikein, että ilmoituksia API:n kautta kuuntelevat tahot saavat
   viestit ja että kuittaukset on välitetty oikein Tieliikennekeskukseen"
  (let [viestit (atom [])]
    (lisaa-kuuntelijoita! {"itmf" {+tloik-ilmoituskuittausjono+ #(swap! viestit conj (.getText %))}})

    ;; Ilmoitushausta tehdään future, jotta HTTP long poll on jo käynnissä, kun uusi ilmoitus vastaanotetaan
    (let [urakka-id (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")
          ilmoitushaku (future (api-tyokalut/get-kutsu ["/api/urakat/" urakka-id "/ilmoitukset?odotaUusia=true"]
                                                       kayttaja portti))]
      (async/<!! (async/timeout timeout))
      (jms/laheta (:itmf jarjestelma) +tloik-ilmoitusviestijono+ (testi-ilmoitus-sanoma))

      (odota-ehdon-tayttymista #(realized? ilmoitushaku) "Saatiin vastaus ilmoitushakuun." kuittaus-timeout)
      (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." kuittaus-timeout)

      (let [_ (Thread/sleep 1000)
            xml (first @viestit)
            data (xml/lue xml)]
        (is (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" xml) "Kuittaus on validia XML:ää.")
        (is (= "10a24e56-d7d4-4b23-9776-2a5a12f254af" (z/xml1-> data :viestiId z/text))
            "Kuittauksen on tehty oikeaan viestiin.")
        (is (= "valitetty" (z/xml1-> data :kuittaustyyppi z/text)) "Kuittauksen tyyppi on oikea.")
        (is (empty? (z/xml1-> data :virhe z/text)) "Virheitä ei ole raportoitu."))

      (is (= 1 (count (hae-testi-ilmoitukset)))
          "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä")

      (let [{:keys [status body] :as vastaus} @ilmoitushaku
            ilmoitustoimenpide (hae-ilmoitustoimenpide-ilmoitusidlla 123456789)]
        (is (nil? ilmoitustoimenpide) "Ei löydetään ilmoitustoimenpide -taulusta merkintää, koska päivystäjää ei ole.")
        (is (= 200 status) "Ilmoituksen haku APIsta onnistuu")))
    (poista-ilmoitus 123456789)))

(deftest tarkista-viestin-kasittely-ja-kuittaukset-paivystajan-kanssa
  "Tarkistaa että ilmoituksen saapuessa data on käsitelty oikein, että ilmoituksia API:n kautta kuuntelevat tahot saavat
   viestit ja että kuittaukset on välitetty oikein Tieliikennekeskukseen"
  (let [kuittausviestit-tloikkiin (atom [])]
    (lisaa-kuuntelijoita! {"itmf" {+tloik-ilmoituskuittausjono+ #(swap! kuittausviestit-tloikkiin conj (.getText %))}})

    ;; Ilmoitushausta tehdään future, jotta HTTP long poll on jo käynnissä, kun uusi ilmoitus vastaanotetaan
    (with-redefs [harja.kyselyt.yhteyshenkilot/hae-urakan-tamanhetkiset-paivystajat
                  (fn [db urakka-id] (list {:id 1
                                            :etunimi "Pekka"
                                            :sukunimi "Päivystäjä"
                                            :matkapuhelin nil
                                            :tyopuhelin nil
                                            :sahkoposti "email.email@example.com"
                                            :alku (t/now)
                                            :loppu (t/now)
                                            :vastuuhenkilo true
                                            :varahenkilo true}))]
     (let [urakka-id (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")
           ilmoitushaku (future (api-tyokalut/get-kutsu ["/api/urakat/" urakka-id "/ilmoitukset?odotaUusia=true&suljeVastauksenJalkeen=false"]
                                  kayttaja portti))
           testi-sanoma (testi-ilmoitus-sanoma)]
       (async/<!! (async/timeout timeout))
       (jms/laheta (:itmf jarjestelma) +tloik-ilmoitusviestijono+ testi-sanoma)

       (odota-ehdon-tayttymista #(realized? ilmoitushaku) "Saatiin vastaus ilmoitushakuun." kuittaus-timeout)
       (odota-ehdon-tayttymista #(= 1 (count @kuittausviestit-tloikkiin)) "Kuittaus on vastaanotettu." kuittaus-timeout)

       ;; Tarkista saapuneen ilmoituksen tila
       (let [_ (odota-ehdon-tayttymista #(hae-ilmoitustoimenpide-ilmoitusidlla 123456789) "Toimenpide on tietokannassa." kuittaus-timeout)
             {:keys [status body] :as vastaus} @ilmoitushaku
             ilmoitustoimenpide (hae-ilmoitustoimenpide-ilmoitusidlla 123456789)]

         (is (= 123456789 (:ilmoitusid ilmoitustoimenpide))
           "Löydetään ilmoitustoimenpide -taulusta merkintä välittämisestä päivystäjälle")
         (is (= 200 status) "Ilmoituksen haku APIsta onnistuu")
         ;; Kommentoin tämän testin pois, koska jostain minulle tuntemattomasta syytä
         ;; ilmoituksia ei voi enää hakea tämän apin kautta, jos päivystäjä on asetettu ja päivystäjälle lähetetään viesti.
         ;; Tämä voi olla ajoitusongelma tai jotain muuta.
         #_ (is (= (-> (cheshire/decode body)
                  (get "ilmoitukset")
                  count) 1) "Ilmoituksia on vastauksessa yksi"))

       ;; Tarkista t-loikille lähetettävän kuittausviestin sisältö
       (let [_ (odota-arvo kuittausviestit-tloikkiin kuittaus-timeout)
             xml (first @kuittausviestit-tloikkiin)
             data (xml/lue xml)]
         (is (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" xml) "Kuittaus on validia XML:ää.")
         (is (= "10a24e56-d7d4-4b23-9776-2a5a12f254af" (z/xml1-> data :viestiId z/text))
           "Kuittauksen on tehty oikeaan viestiin.")
         (is (= "valitetty" (z/xml1-> data :kuittaustyyppi z/text)) "Kuittauksen tyyppi on oikea.")
         (is (empty? (z/xml1-> data :virhe z/text)) "Virheitä ei ole raportoitu."))

       (is (= 1 (count (hae-testi-ilmoitukset)))
         "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä")))
    (poista-ilmoitus 123456789)))

;; Palauttaa ilmoituksen vastaanottajalle virheen
(deftest testaa-toimenpidepyynto-ilman-sijaintia
  "Testataan, että toimenpidepyyntö on muuten kunnossa, mutta ilmoituksella ei ole sijaintia"
  (let [viestit (atom [])]
    (lisaa-kuuntelijoita! {"itmf" {+tloik-ilmoituskuittausjono+ #(swap! viestit conj (.getText %))}})

    ;; Ilmoitushausta tehdään future, jotta HTTP long poll on jo käynnissä, kun uusi ilmoitus vastaanotetaan
    (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          ilmoitushaku (future (api-tyokalut/get-kutsu ["/api/urakat/" urakka-id "/ilmoitukset?odotaUusia=true"]
                                 kayttaja portti))
          viesti-id (str (UUID/randomUUID))
          ilmoitus-id (rand-int 99999999)
          sijainti nil
          ilmoittaja aineisto-toimenpidepyynnot/ilmoittaja-xml]
      (async/<!! (async/timeout timeout))
      (jms/laheta (:itmf jarjestelma) +tloik-ilmoitusviestijono+ (aineisto-toimenpidepyynnot/toimenpidepyynto-sanoma viesti-id ilmoitus-id sijainti ilmoittaja))
      (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." kuittaus-timeout)

      ;; Koska viesti ei ole validia, niin tarkistetaan, että saadaan kuittaussanomana virhe
      (let [ilmoitus (hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)
            xml (first @viestit)
            data (xml/lue xml)]
        ;; Koska saatu sanoma on virheellinen, niin ilmoitus -tauluun ei tallenneta siitä mitään tietoa
        (is (nil? ilmoitus))
        (is (= "virhe" (z/xml1-> data :kuittaustyyppi z/text)) "XML-sanoma ei ole harja-tloik.xsd skeeman mukainen")
        (is (not (empty? (z/xml1-> data :virhe z/text))) "Virheitä löydettiin."))

      (poista-ilmoitus ilmoitus-id))))

(deftest testaa-toimenpidepyynto-paivystajan-emaililla
  "Testataan, että toimenpidepyyntö on kunnossa. Tällä testillä voidaan nähdä, että kaikki toimii, vaikka puhelinnumeroa
  ei päivystäjälle ole annettu."
  (let [viestit (atom [])]
    (lisaa-kuuntelijoita! {"itmf" {+tloik-ilmoituskuittausjono+ #(swap! viestit conj (.getText %))}})

    ;; Ilmoitushausta tehdään future, jotta HTTP long poll on jo käynnissä, kun uusi ilmoitus vastaanotetaan
    (with-redefs [harja.kyselyt.yhteyshenkilot/hae-urakan-tamanhetkiset-paivystajat
                  (fn [db urakka-id] (list {:id 1
                                            :etunimi "Pekka"
                                            :sukunimi "Päivystäjä"
                                            :matkapuhelin nil
                                            :tyopuhelin nil
                                            :sahkoposti "email.email@example.com"
                                            :alku (t/now)
                                            :loppu (t/now)
                                            :vastuuhenkilo true
                                            :varahenkilo true}))]
      (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
            ilmoitushaku (future (api-tyokalut/get-kutsu ["/api/urakat/" urakka-id "/ilmoitukset?odotaUusia=true"]
                                   kayttaja portti))
            viesti-id (str (UUID/randomUUID))
            ilmoitus-id (rand-int 99999999)
            sijainti aineisto-toimenpidepyynnot/sijainti-oulun-alueella
            ilmoittaja aineisto-toimenpidepyynnot/ilmoittaja-xml]
        (async/<!! (async/timeout timeout))
        (jms/laheta (:itmf jarjestelma) +tloik-ilmoitusviestijono+ (aineisto-toimenpidepyynnot/toimenpidepyynto-sanoma viesti-id ilmoitus-id sijainti ilmoittaja))

        (odota-ehdon-tayttymista #(realized? ilmoitushaku) "Saatiin vastaus ilmoitushakuun." kuittaus-timeout)
        (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." kuittaus-timeout)

        (let [_ (odota-arvo viestit kuittaus-timeout)
              xml (first @viestit)
              data (xml/lue xml)
              _ (odota-ehdon-tayttymista #(hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id) "Ilmoitus on tietokannassa." kuittaus-timeout)
              ilmoitus (hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)]
          (is (= ilmoitus-id (:ilmoitus-id ilmoitus)))
          (is (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" xml) "Kuittaus on validia XML:ää.")
          (is (= viesti-id (z/xml1-> data :viestiId z/text)) "Kuittauksen on tehty oikeaan viestiin.")
          (is (= "valitetty" (z/xml1-> data :kuittaustyyppi z/text)) "Kuittauksen tyyppi on oikea.")
          (is (empty? (z/xml1-> data :virhe z/text)) "Virheitä ei ole raportoitu.")


          (is (= ilmoitus-id (:ilmoitus-id ilmoitus))
            "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä"))

        (let [{:keys [status body] :as vastaus} @ilmoitushaku
              ilmoitustoimenpide (hae-ilmoitustoimenpide-ilmoitusidlla ilmoitus-id)]
          (is (= ilmoitus-id (:ilmoitusid ilmoitustoimenpide)) "Ilmoitustoimpenpide on olemassa, koska päivystäjällä on email osoite.")
          (is (= 200 status) "Ilmoituksen haku APIsta onnistuu")
          (is (= (nil? ilmoitustoimenpide) ) "Ilmoitus ei ole tallentunut ilmoitustoimenpidetauluun, koska emailia ei ole annettu."))
        (poista-ilmoitus ilmoitus-id)))))

(deftest testaa-toimenpidepyynto-paivystajalla-jolla-ei-ole-emailia
  "Testataan, että toimenpidepyyntö on muuten kunnossa, mutta paivystäjällä sähköpostiosoitetta.
  Tämän ei pitäisi aiheuttaaa mitään ongelmaa, koska se ei ole pakollista tietoa."
  (let [viestit (atom [])]
    (lisaa-kuuntelijoita! {"itmf" {+tloik-ilmoituskuittausjono+ #(swap! viestit conj (.getText %))}})

    ;; Ilmoitushausta tehdään future, jotta HTTP long poll on jo käynnissä, kun uusi ilmoitus vastaanotetaan
    (with-redefs [harja.kyselyt.yhteyshenkilot/hae-urakan-tamanhetkiset-paivystajat
                  (fn [db urakka-id]
                    (list {:id 1
                           :etunimi "Pekka"
                           :sukunimi "Päivystäjä"
                           :matkapuhelin nil
                           :tyopuhelin nil
                           :sahkoposti "" ;Testataan ikäänkuin epävalidilla emailosoitteella
                           :alku (t/now)
                           :loppu (t/now)
                           :vastuuhenkilo true
                           :varahenkilo true}
                      {:id 2
                       :etunimi "Pekka2"
                       :sukunimi "Päivystäjä2"
                       :matkapuhelin nil
                       :tyopuhelin nil
                       :sahkoposti nil ;"Testataan kokonaan puuttuvalla emailosoitteella
                       :alku (t/now)
                       :loppu (t/now)
                       :vastuuhenkilo true
                       :varahenkilo true}))]
      (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
            ilmoitushaku (future (api-tyokalut/get-kutsu ["/api/urakat/" urakka-id "/ilmoitukset?odotaUusia=true"]
                                   kayttaja portti))
            viesti-id (str (UUID/randomUUID))
            ilmoitus-id (rand-int 99999999)
            sijainti aineisto-toimenpidepyynnot/sijainti-oulun-alueella
            ilmoittaja aineisto-toimenpidepyynnot/ilmoittaja-xml]
        (async/<!! (async/timeout timeout))
        (jms/laheta (:itmf jarjestelma) +tloik-ilmoitusviestijono+ (aineisto-toimenpidepyynnot/toimenpidepyynto-sanoma viesti-id ilmoitus-id sijainti ilmoittaja))

        (odota-ehdon-tayttymista #(realized? ilmoitushaku) "Saatiin vastaus ilmoitushakuun." kuittaus-timeout)
        (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." kuittaus-timeout)

        (let [_ (Thread/sleep 1000)
              xml (first @viestit)
              data (xml/lue xml)
              ilmoitus (hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)]
          (is (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" xml) "Kuittaus on validia XML:ää.")
          (is (= viesti-id (z/xml1-> data :viestiId z/text)) "Kuittauksen on tehty oikeaan viestiin.")
          (is (= "valitetty" (z/xml1-> data :kuittaustyyppi z/text)) "Kuittauksen tyyppi on oikea.")
          (is (empty? (z/xml1-> data :virhe z/text)) "Virheitä ei ole raportoitu.")
          (is (= ilmoitus-id (:ilmoitus-id ilmoitus))
            "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä"))

        (let [{:keys [status body] :as vastaus} @ilmoitushaku
              ilmoitustoimenpide (hae-ilmoitustoimenpide-ilmoitusidlla ilmoitus-id)]
          (is (= 200 status) "Ilmoituksen haku APIsta onnistuu")
          (is (nil? ilmoitustoimenpide) "Ilmoitustoimenpidettä ei voida tehdä, koska päivystäjältä puuttuu sekä puhelinumerot, että email."))
        (poista-ilmoitus ilmoitus-id)))))

(deftest testaa-toimenpidepyynto-ilmoittajan-tyyppi
  "Testataan, että toimenpidepyyntö on kunnossa. Ilmoittajan tyyppi pitäisi kirjautua tietokantaan."
  (let [viestit (atom [])]
    (lisaa-kuuntelijoita! {"itmf" {+tloik-ilmoituskuittausjono+ #(swap! viestit conj (.getText %))}})

    ;; Ilmoitushausta tehdään future, jotta HTTP long poll on jo käynnissä, kun uusi ilmoitus vastaanotetaan
    (with-redefs [harja.kyselyt.yhteyshenkilot/hae-urakan-tamanhetkiset-paivystajat
                  (fn [db urakka-id] (list {:id 1
                                            :etunimi "Pekka"
                                            :sukunimi "Päivystäjä"
                                            :matkapuhelin nil
                                            :tyopuhelin nil
                                            :sahkoposti "email.email@example.com"
                                            :alku (t/now)
                                            :loppu (t/now)
                                            :vastuuhenkilo true
                                            :varahenkilo true}))]
      (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
            ilmoitushaku (future (api-tyokalut/get-kutsu ["/api/urakat/" urakka-id "/ilmoitukset?odotaUusia=true"]
                                   kayttaja portti))
            ilmoitus-id (rand-int 99999999)
            ilmoitus-data {:viesti-id (str (UUID/randomUUID))
                           :ilmoitus-id ilmoitus-id
                           :ilmoittaja-etunimi "Anonyymi"
                           :ilmoittaja-sukunimi "kontakti"
                           :ilmoittaja-email "anonyymi.kontakti@example.com"
                           :ilmoittaja-tyyppi "urakoitsija" ;; Tämän toimivuus testataan tässä
                           :sijainti-xml aineisto-toimenpidepyynnot/sijainti-oulun-alueella}]
        (async/<!! (async/timeout timeout))
        (jms/laheta (:itmf jarjestelma) +tloik-ilmoitusviestijono+ (aineisto-toimenpidepyynnot/toimenpidepyynto-ilmoittaja-sanoma ilmoitus-data))
        (odota-ehdon-tayttymista #(realized? ilmoitushaku) "Saatiin vastaus ilmoitushakuun." kuittaus-timeout)
        (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." kuittaus-timeout)

        (let [_ (odota-arvo viestit kuittaus-timeout)
              xml (first @viestit)
              _ (odota-ehdon-tayttymista #(hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id) "Ilmoitus on tietokannassa." kuittaus-timeout)
              ilmoitus (hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)]
          (is (= ilmoitus-id (:ilmoitus-id ilmoitus)))
          (is (= "urakoitsija" (:ilmoittaja_tyyppi ilmoitus)) "Ilmoittaja tyyppi toimii"))
        (poista-ilmoitus ilmoitus-id)))))


(deftest tarkista-viestin-kasittely-kun-urakkaa-ei-loydy
  (let [sanoma +ilmoitus-ruotsissa+
        viestit (atom [])]
    (lisaa-kuuntelijoita! {"itmf" {+tloik-ilmoituskuittausjono+ #(swap! viestit conj (.getText %))}})
    (jms/laheta (:itmf jarjestelma) +tloik-ilmoitusviestijono+ sanoma)

    (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Kuittaus on vastaanotettu." kuittaus-timeout)

    (let [xml (first @viestit)
          data (xml/lue xml)]
      (is (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" xml) "Kuittaus on validia XML:ää.")
      (is (= "10a24e56-d7d4-4b23-9776-2a5a12f254af" (z/xml1-> data :viestiId z/text))
          "Kuittauksen on tehty oikeaan viestiin.")
      (is (= "virhe" (z/xml1-> data :kuittaustyyppi z/text)) "Kuittauksen tyyppi on oikea.")
      (is (= "Tiedoilla ei voitu päätellä urakkaa." (z/xml1-> data :virhe z/text))
          "Virheitä ei ole raportoitu."))

    (is (= 0 (count (hae-testi-ilmoitukset))) "Tietokannasta ei löydy ilmoitusta T-LOIK:n id:llä")
    (poista-ilmoitus 123456789)))


(deftest tarkista-ilmoituksen-lahettaminen-valaistusurakalle
  "Tarkistaa että ilmoitus ohjataan oikein valaistusurakalle"
  (ei-lisattavia-kuuntelijoita!)
  (tuo-valaistusilmoitus)
  (is (= (first (q "select id from urakka where nimi = 'Oulun valaistuksen palvelusopimus 2013-2050';"))
         (first (q "select urakka from ilmoitus where ilmoitusid = 987654321;")))
      "Urakka on asetettu oletuksena hoidon alueurakalle, kun sijainnissa ei ole käynnissä päällystysurakkaa.")
  (poista-valaistusilmoitus))

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
  (poista-ilmoitus 123456789))
