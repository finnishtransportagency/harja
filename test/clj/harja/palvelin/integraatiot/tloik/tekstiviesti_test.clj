(ns harja.palvelin.integraatiot.tloik.tekstiviesti-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [clojure.data.zip.xml :as z]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.kyselyt.paivystajatekstiviestit :as paivystajatekstiviestit]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.palvelin.integraatiot.labyrintti.sms :refer [->Labyrintti]]
            [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.tloik.tekstiviesti :as tekstiviestit]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.tyokalut.xml :as xml]
            [clojure.string :as str]))

(def kayttaja "jvh")
(def +labyrintti-url+ "http://localhost:28080/sendsms")

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
                                                    :sahkoposti-sisaan-kuittausjono "email-to-harja-ack"
                                                    :sahkoposti-ulos-jono "harja-to-email"
                                                    :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                        [:sonja :db :integraatioloki])
    :labyrintti (component/using
                  (labyrintti/luo-labyrintti
                    {:url +labyrintti-url+
                     :kayttajatunnus "solita-2" :salasana "ne8aCrasesev"})
                  [:db :http-palvelin :integraatioloki])
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :sonja :integraatioloki :klusterin-tapahtumat :labyrintti])))

(use-fixtures :each jarjestelma-fixture)

(deftest tarkista-kuittauksen-vastaanotto-tekstiviestilla
  (tuo-ilmoitus)
  (let [integraatioloki (:integraatioloki jarjestelma)
        db (:db jarjestelma)
        lokittaja (integraatioloki/lokittaja integraatioloki db "tloik" "toimenpiteen-lahetys")
        jms-lahettaja (jms/jonolahettaja lokittaja (:sonja jarjestelma) +tloik-ilmoitustoimenpideviestijono+)
        ilmoitus (first (hae-testi-ilmoitukset))
        ilmoitus-id (:ilmoitus-id ilmoitus)
        yhteyshenkilo (tee-testipaivystys)
        yhteyshenkilo-id (first yhteyshenkilo)
        puhelinnumero (second yhteyshenkilo)]

    (paivystajatekstiviestit/kirjaa-uusi-paivystajatekstiviesti<! db puhelinnumero ilmoitus-id yhteyshenkilo-id)

    (is (= "Viestiäsi ei voitu käsitellä. Antamasi kuittaus ei ole validi. Vastaa viestiin kuittauskoodilla ja kommentilla."
           (tekstiviestit/vastaanota-tekstiviestikuittaus jms-lahettaja db "0834" "TESTI"))
        "Tuntematon käyttäjä käsitellään oikein")
    (is (= "Viestiä ei voida käsitellä. Kuittauskoodi puuttuu."
           (tekstiviestit/vastaanota-tekstiviestikuittaus jms-lahettaja db puhelinnumero ""))
        "Puuttuva toimenpide tai viestinumero käsitellään oikein.")

    (is (= "Viestiäsi ei voitu käsitellä. Antamallasi viestinumerolla ei löydy avointa ilmoitusta. Vastaa viestiin kuittauskoodilla ja kommentilla."
           (tekstiviestit/vastaanota-tekstiviestikuittaus jms-lahettaja db puhelinnumero "V2"))
        "Tuntematon viestinumero käsitellään oikein.")

    (let [viesti (atom nil)]
      (sonja/kuuntele (:sonja jarjestelma) +tloik-ilmoitustoimenpideviestijono+ #(reset! viesti (.getText %)))

      (is (= "Kuittaus käsiteltiin onnistuneesti. Kiitos!"
             (tekstiviestit/vastaanota-tekstiviestikuittaus jms-lahettaja db puhelinnumero "V1 Asia selvä."))
          "Onnistunut viestin käsittely")

      (let [xml (odota-arvo viesti)
            data (xml/lue xml)]
        (is (= "123456789" (z/xml1-> data :ilmoitusId z/text)) "Kuittaus on tehty oikeaan viestiin.")
        (is (= "vastaanotto" (z/xml1-> data :tyyppi z/text)) "Kuittaus on tehty oikeaan viestiin.")
        (is (= "Asia selvä." (z/xml1-> data :vapaateksti z/text)) "Kuittaus on tehty oikeaan viestiin.")))

    (poista-paivystajatekstiviestit)
    (poista-ilmoitus)))

(deftest tarkista-ilmoituksen-lahettaminen-tekstiviestilla
  (tuo-ilmoitus)
  (let [fake-vastaus [{:url +labyrintti-url+ :method :post} {:status 200}]
        paivystaja (hae-paivystaja)
        paivystaja {:id (first paivystaja) :matkapuhelin (second paivystaja)}
        ilmoitus (first (hae-testi-ilmoitukset))
        paivystajaviestien-maara (fn []
                                   (count
                                     (q (format "select * from paivystajatekstiviesti where yhteyshenkilo = %s and ilmoitus = %s;"
                                                (:id paivystaja)
                                                (:id ilmoitus)))))]
    (with-fake-http
      [{:url +labyrintti-url+ :method :get} fake-vastaus]

      (let [viestien-maara-ennen (paivystajaviestien-maara)]

        (tekstiviestit/laheta-ilmoitus-tekstiviestilla (:labyrintti jarjestelma)
                                                       (:db jarjestelma)
                                                       ilmoitus
                                                       paivystaja)

        (is (= (inc viestien-maara-ennen) (paivystajaviestien-maara))))

      (poista-paivystajatekstiviestit)
      (poista-ilmoitus))))

(deftest tekstiviestin-muodostus
  (let [ilmoitus {:tunniste "UV666"
                  :otsikko "Testiympäristö liekeissä!"
                  :paikankuvaus "Konesali"
                  :sijainti {:tr-numero 1
                             :tr-alkuosa 2
                             :tr-alkuetaisyys 3
                             :tr-loppuosa 4
                             :tr-loppuetaisyys 5}
                  :lisatieto "Soittakaapa äkkiä"
                  :yhteydenottopyynto true
                  :selitteet #{:toimenpidekysely}}
        rivit (into #{} (str/split-lines
                          (tekstiviestit/ilmoitus-tekstiviesti ilmoitus 1234)))]
    (is (rivit "Uusi toimenpidepyyntö : Testiympäristö liekeissä! (tunniste: UV666, viestinumero: 1234)."))
    (is (rivit "Yhteydenottopyyntö: Kyllä"))
    (is (rivit "Paikka: Konesali"))
    (is (rivit "Lisätietoja: Soittakaapa äkkiä."))
    (is (rivit "TR-osoite: 1 / 2 / 3 / 4 / 5"))
    (is (rivit "Selitteet: Toimenpidekysely."))))

(deftest tekstiviestin-muodostus-pisteelle
  (let [ilmoitus {:tunniste "UV666"
                  :otsikko "Testiympäristö liekeissä!"
                  :paikankuvaus "Konesali"
                  :sijainti {:tr-numero 1
                             :tr-alkuosa 2
                             :tr-alkuetaisyys 3}
                  :lisatieto "Soittakaapa äkkiä"
                  :yhteydenottopyynto true
                  :selitteet #{:toimenpidekysely}}
        rivit (into #{} (str/split-lines
                          (tekstiviestit/ilmoitus-tekstiviesti ilmoitus 1234)))]
    (is (rivit "Uusi toimenpidepyyntö : Testiympäristö liekeissä! (tunniste: UV666, viestinumero: 1234)."))
    (is (rivit "Yhteydenottopyyntö: Kyllä"))
    (is (rivit "Paikka: Konesali"))
    (is (rivit "Lisätietoja: Soittakaapa äkkiä."))
    (is (rivit "TR-osoite: 1 / 2 / 3"))
    (is (rivit "Selitteet: Toimenpidekysely."))))

(deftest tekstiviestin-muodostus-ilman-tr-osoitetta
  (let [ilmoitus {:tunniste "UV666"
                  :otsikko "Testiympäristö liekeissä!"
                  :paikankuvaus "Kilpisjärvi"
                  :lisatieto "Soittakaapa äkkiä"
                  :yhteydenottopyynto false
                  :selitteet #{:toimenpidekysely}}
        rivit (into #{} (str/split-lines
                          (tekstiviestit/ilmoitus-tekstiviesti ilmoitus 1234)))]
    (is (rivit "Uusi toimenpidepyyntö : Testiympäristö liekeissä! (tunniste: UV666, viestinumero: 1234)."))
    (is (rivit "Yhteydenottopyyntö: Ei"))
    (is (rivit "Paikka: Kilpisjärvi"))
    (is (rivit "Lisätietoja: Soittakaapa äkkiä."))
    (is (rivit "TR-osoite: Ei tierekisteriosoitetta"))
    (is (rivit "Selitteet: Toimenpidekysely."))))
