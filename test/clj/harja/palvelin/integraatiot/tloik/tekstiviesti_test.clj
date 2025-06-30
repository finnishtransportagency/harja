(ns harja.palvelin.integraatiot.tloik.tekstiviesti-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.domain.tieliikenneilmoitukset :as apurit]
            [harja.kyselyt.palautevayla :as palautevayla-q]
            [harja.palvelin.integraatiot.sms.sms-komponentti :as sms]
            [harja.testi :refer :all]
            [harja.jms-test :refer [feikki-jms]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
            [harja.palvelin.integraatiot.tloik.tekstiviesti :as tekstiviestit]
            [clojure.string :as str]))

(def kayttaja "jvh")
(def +sms-url+ "http://localhost:28080/sms")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-ilmoitukset (component/using
                       (api-ilmoitukset/->Ilmoitukset)
                       [:http-palvelin :db :integraatioloki])
    :itmf (feikki-jms "itmf")
    :api-sahkoposti (component/using
                       (sahkoposti-api/->ApiSahkoposti {:tloik {:toimenpidekuittausjono "Harja.HarjaToT-LOIK.Ack"}})
                       [:http-palvelin :db :integraatioloki :itmf])
    :sms (component/using
                  (sms/luo-tekstiviesti-komponentti
                    {:url +sms-url+
                     :apiavain "miu"})
                  [:db :http-palvelin :integraatioloki])
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :itmf :integraatioloki :sms :api-sahkoposti])))

(defn tekstiviestin-rivit [ilmoitus]
  (into #{} (str/split-lines
              (tekstiviestit/ilmoitus-tekstiviesti ilmoitus 1234 (palautevayla-q/hae-aiheet-ja-tarkenteet (:db jarjestelma))))))

(use-fixtures :each jarjestelma-fixture)

(defn ilmoitus-aiheutti-toimenpiteita? [id]
  (ffirst (q (str "SELECT \"aiheutti-toimenpiteita\" FROM ilmoitus WHERE id = " id ";"))))


(deftest tekstiviestin-muodostus
  (let [ilmoitus {:tunniste "UV666"
                  :otsikko "Testiympäristö liekeissä!"
                  :paikankuvaus "Konesali"
                  :urakkanimi "Testiurakka"
                  :sijainti {:tr-numero 1
                             :tr-alkuosa 2
                             :tr-alkuetaisyys 3
                             :tr-loppuosa 4
                             :tr-loppuetaisyys 5}
                  :ilmoittaja {:etunimi "Heikki"
                              :sukunimi "Hervoton"
                              :matkapuhelin "0401234567"
                              :sahkoposti "ananasakaama@testimaili.com"}
                  :lahettaja {:etunimi "Erkki"
                              :sukunimi "Esimerkki"
                              :organisaatio "TestiOrg"
                              :ytunnus "12348589"
                              :tyopuhelin "0401234567"
                              :matkapuhelin "0401234567"
                              :sahkoposti "erkki.esimerkki@testiorg.org"}
                  :lisatieto "Soittakaapa äkkiä"
                  :yhteydenottopyynto true
                  :selitteet #{:toimenpidekysely}}
        rivit (tekstiviestin-rivit ilmoitus)
        _ (println rivit)]
    (is (rivit "TPP: UV666"))
    (is (rivit "Selitteet: Toimenpidekysely."))
    (is (rivit "Testiurakka"))
    (is (rivit "Tieosoite: 1 / 2 / 3 / 4 / 5"))
    (is (rivit "Paikka: Konesali"))
    (is (rivit (str "Ilmoittaja: " (apurit/nayta-henkilon-yhteystiedot (:ilmoittaja ilmoitus)))))
    (is (rivit "Yhteydenotto: Kyllä"))
    (is (rivit (str "Lähettäjä: " (apurit/nayta-henkilon-yhteystiedot (:lahettaja ilmoitus)))))
    (is (rivit "Lisätietoja: Soittakaapa äkkiä."))))

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
        rivit (tekstiviestin-rivit ilmoitus)]
    (is (rivit "TPP: UV666"))
    (is (rivit "Selitteet: Toimenpidekysely."))
    (is (rivit "Tieosoite: 1 / 2 / 3"))
    (is (rivit "Yhteydenotto: Kyllä"))
    (is (rivit "Paikka: Konesali"))
    (is (rivit "Lisätietoja: Soittakaapa äkkiä."))
    (is (rivit "Selitteet: Toimenpidekysely."))))

(deftest tekstiviestin-muodostus-ilman-tr-osoitetta
  (let [ilmoitus {:tunniste "UV666"
                  :otsikko "Testiympäristö liekeissä!"
                  :paikankuvaus "Kilpisjärvi"
                  :lisatieto "Soittakaapa äkkiä"
                  :yhteydenottopyynto false
                  :selitteet #{:toimenpidekysely}}
        rivit (tekstiviestin-rivit ilmoitus)]
    (is (rivit "TPP: UV666"))
    (is (rivit "Selitteet: Toimenpidekysely."))
    (is (rivit "Tieosoite: Ei tieosoitetta"))
    (is (rivit "Paikka: Kilpisjärvi"))
    (is (rivit "Lisätietoja: Soittakaapa äkkiä."))
    (is (rivit "Selitteet: Toimenpidekysely."))))

(deftest tekstiviestin-muodostus-aiheella
  (let [ilmoitus {:tunniste "UV666"
                  :otsikko "Testiympäristö liekeissä!"
                  :paikankuvaus "Konesali"
                  :urakkanimi "Testiurakka"
                  :sijainti {:tr-numero 1
                             :tr-alkuosa 2
                             :tr-alkuetaisyys 3
                             :tr-loppuosa 4
                             :tr-loppuetaisyys 5}
                  :ilmoittaja {:etunimi "Heikki"
                               :sukunimi "Hervoton"
                               :matkapuhelin "0401234567"
                               :sahkoposti "ananasakaama@testimaili.com"}
                  :lahettaja {:etunimi "Erkki"
                              :sukunimi "Esimerkki"
                              :organisaatio "TestiOrg"
                              :ytunnus "12348589"
                              :tyopuhelin "0401234567"
                              :matkapuhelin "0401234567"
                              :sahkoposti "erkki.esimerkki@testiorg.org"}
                  :lisatieto "Soittakaapa äkkiä"
                  :yhteydenottopyynto true
                  :luokittelu {:aihe 900
                               :tarkenne 9001}}
        rivit (tekstiviestin-rivit ilmoitus)]
    (is (rivit "TPP: UV666"))
    (is (rivit "Testaus"))
    (is (rivit "Testaaminen"))
    (is (rivit "Testiurakka"))
    (is (rivit "Tieosoite: 1 / 2 / 3 / 4 / 5"))
    (is (rivit (str "Ilmoittaja: " (apurit/nayta-henkilon-yhteystiedot (:ilmoittaja ilmoitus)))))
    (is (rivit "Yhteydenotto: Kyllä"))
    (is (rivit (str "Lähettäjä: " (apurit/nayta-henkilon-yhteystiedot (:lahettaja ilmoitus)))))
    (is (rivit "Paikka: Konesali"))
    (is (rivit "Lisätietoja: Soittakaapa äkkiä."))))


(deftest tekstiviestin-parsinta
  (is (= (tekstiviestit/parsi-tekstiviesti "V3")
         {:toimenpide "vastaanotto" :viestinumero 3 :vapaateksti "" :aiheutti-toimenpiteita false})
      "Perustapaus osataan parsia oikein")

  (is (= (tekstiviestit/parsi-tekstiviesti "V3Jotain")
         {:toimenpide "vastaanotto" :viestinumero 3 :vapaateksti "Jotain" :aiheutti-toimenpiteita false})
      "Vapaateksti osataan parsia oikein")

  (is (= (tekstiviestit/parsi-tekstiviesti "V3 Jotain jännää")
         {:toimenpide "vastaanotto" :viestinumero 3 :vapaateksti "Jotain jännää" :aiheutti-toimenpiteita false})
      "Vapaateksti osataan parsia oikein välilyönteineen")

  (is (= (tekstiviestit/parsi-tekstiviesti "V666 Jotain jännää")
         {:toimenpide "vastaanotto" :viestinumero 666 :vapaateksti "Jotain jännää" :aiheutti-toimenpiteita false})
      "Moninumeroinen viestinumero osataan parsia oikein")

  (is (= (tekstiviestit/parsi-tekstiviesti "T666 Jotain jännää")
         {:toimenpide "lopetus" :viestinumero 666 :vapaateksti "Jotain jännää" :aiheutti-toimenpiteita true})
      "Toimenpiteitä aiheuttanut lopetuskuittaus osataan tulkita oikein")

  (is (thrown? Exception (tekstiviestit/parsi-tekstiviesti "666"))
      "Poikkeus heitetään, kun kuittaustyyppi uupuu")

  (is (thrown? Exception (tekstiviestit/parsi-tekstiviesti "V"))
      "Poikkeus heitetään, kun viestinumero uupuu")

  (is (thrown? Exception (tekstiviestit/parsi-tekstiviesti "1V"))
      "Poikkeus heitetään, kun kuittaustyyppiä & viestinumeroa ei saada parsittua"))
