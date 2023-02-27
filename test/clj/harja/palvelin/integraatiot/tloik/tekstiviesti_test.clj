(ns harja.palvelin.integraatiot.tloik.tekstiviesti-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [clojure.data.zip.xml :as z]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.jms-test :refer [feikki-jms]]
            [harja.kyselyt.paivystajatekstiviestit :as paivystajatekstiviestit]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.palvelin.integraatiot.labyrintti.sms :refer [->Labyrintti]]
            [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]
            [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.tloik.tekstiviesti :as tekstiviestit]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [harja.palvelin.integraatiot.jms :as jms-util]
            [harja.tyokalut.xml :as xml]
            [clojure.string :as str]))

(def kayttaja "jvh")
(def +labyrintti-url+ "http://localhost:28080/sendsms")

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
    :labyrintti (component/using
                  (labyrintti/luo-labyrintti
                    {:url +labyrintti-url+
                     :kayttajatunnus "solita-2" :salasana "ne8aCrasesev"})
                  [:db :http-palvelin :integraatioloki])
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :itmf :integraatioloki :labyrintti :api-sahkoposti])))

(defn tekstiviestin-rivit [ilmoitus]
  (into #{} (str/split-lines
              (tekstiviestit/ilmoitus-tekstiviesti ilmoitus 1234))))

(use-fixtures :each jarjestelma-fixture)

(defn ilmoitus-aiheutti-toimenpiteita? [id]
  (ffirst (q (str "SELECT \"aiheutti-toimenpiteita\" FROM ilmoitus WHERE id = " id ";"))))


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
        rivit (tekstiviestin-rivit ilmoitus)]
    (is (rivit "Uusi toimenpidepyyntö : Testiympäristö liekeissä! (viestinumero: 1234)."))
    (is (rivit "Yhteydenottopyyntö: Kyllä"))
    (is (rivit "Paikka: Konesali"))
    (is (rivit "Lisätietoja: Soittakaapa äkkiä."))
    (is (rivit "Tienumero: 1 / 2 / 3 / 4 / 5"))
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
        rivit (tekstiviestin-rivit ilmoitus)]
    (is (rivit "Uusi toimenpidepyyntö : Testiympäristö liekeissä! (viestinumero: 1234)."))
    (is (rivit "Yhteydenottopyyntö: Kyllä"))
    (is (rivit "Paikka: Konesali"))
    (is (rivit "Lisätietoja: Soittakaapa äkkiä."))
    (is (rivit "Tienumero: 1 / 2 / 3"))
    (is (rivit "Selitteet: Toimenpidekysely."))))

(deftest tekstiviestin-muodostus-ilman-tr-osoitetta
  (let [ilmoitus {:tunniste "UV666"
                  :otsikko "Testiympäristö liekeissä!"
                  :paikankuvaus "Kilpisjärvi"
                  :lisatieto "Soittakaapa äkkiä"
                  :yhteydenottopyynto false
                  :selitteet #{:toimenpidekysely}}
        rivit (tekstiviestin-rivit ilmoitus)]
    (is (rivit "Uusi toimenpidepyyntö : Testiympäristö liekeissä! (viestinumero: 1234)."))
    (is (rivit "Yhteydenottopyyntö: Ei"))
    (is (rivit "Paikka: Kilpisjärvi"))
    (is (rivit "Lisätietoja: Soittakaapa äkkiä."))
    (is (rivit "Tienumero: Ei tierekisteriosoitetta"))
    (is (rivit "Selitteet: Toimenpidekysely."))))



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
