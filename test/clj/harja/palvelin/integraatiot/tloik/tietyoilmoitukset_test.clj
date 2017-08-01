(ns harja.palvelin.integraatiot.tloik.tietyoilmoitukset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [hiccup.core :refer [html]]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.sonja :as sonja]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.palvelin.integraatiot.labyrintti.sms :refer [->Labyrintti]]
            [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :as tloik]
            [harja.domain.tietyoilmoitukset :as tietyilmoitukset-d]
            [harja.kyselyt.tietyoilmoitukset :as tietyoilmoitukset-q]
            [clojure.string :as str]))

(def kayttaja "yit-rakennus")

(def +kuittaussanoma+
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
  <harja:harja-kuittaus xmlns:harja=\"http://www.liikennevirasto.fi/xsd/harja\">
  <aika>2015-12-03T07:40:40.811Z</aika>
  <kuittaustyyppi>vastaanotettu</kuittaustyyppi>
  <viestiId>[VIESTI-ID]</viestiId>
  </harja:harja-kuittaus>")

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
                    {:url "http://localhost:28080/sendsms"
                     :kayttajatunnus "solita-2" :salasana "ne8aCrasesev"})
                  [:db :http-palvelin :integraatioloki])
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :sonja :integraatioloki :klusterin-tapahtumat :labyrintti])))

(use-fixtures :once jarjestelma-fixture)

(deftest tarkista-lahetys-ja-kuittaus
  (let [tietyoilmoitus-id 1
        viestit (atom [])
        sonja (:sonja jarjestelma)]

    (sonja/kuuntele sonja +tloik-tietyoilmoitusviestijono+ #(swap! viestit conj (.getText %)))
    (tloik/laheta-tietyilmoitus (:tloik jarjestelma) tietyoilmoitus-id)

    (odota-ehdon-tayttymista #(= 1 (count @viestit)) "Tietyöilmoitussanoma on vastaanotettu." 10000)

    (let [xml (first @viestit)
          data (xml/lue xml)
          viesti-id (z/xml1-> data :viestiId z/text)
          tietyoilmoitus-lahetyksen-jalkeen (tietyoilmoitukset-q/hae-ilmoitus ds tietyoilmoitus-id)
          kuittaussanoma (str/replace +kuittaussanoma+ "[VIESTI-ID]" viesti-id)]
      (is (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" xml) "Lähetetty viesti on validia XML:ää.")
      (is (= "odottaa_vastausta" (::tietyilmoitukset-d/tila tietyoilmoitus-lahetyksen-jalkeen)))

      (sonja/laheta sonja +tloik-tietyoilmoituskuittausjono+ kuittaussanoma)
      (odota-ehdon-tayttymista #(tietyoilmoitukset-q/lahetetty? ds tietyoilmoitus-id)
                               "Tietyöilmoitus on merkitty lähetetyksi." 10000))))
