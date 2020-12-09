(ns ^:integraatio tarkkailija.palvelin.komponentit.uudelleen-kaynnistaja-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.integraatio :as integraatio]
            [tarkkailija.palvelin.komponentit.uudelleen-kaynnistaja :as uudelleen-kaynnistaja]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.sonja :as sonja]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.palvelin.integraatiot.labyrintti.sms :refer [->Labyrintti]]
            [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]
            [harja.palvelin.integraatiot.sonja.tyokalut :as s-tk]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.pvm :as pvm]
            [clj-time
             [coerce :as tc]
             [format :as df]]))

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
                  (labyrintti/luo-labyrintti
                    {:url "http://localhost:28080/sendsms"
                     :kayttajatunnus "solita-2" :salasana "ne8aCrasesev"})
                  [:db :http-palvelin :integraatioloki])
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :sonja :integraatioloki :labyrintti :sonja-sahkoposti])))

(use-fixtures :each (fn [testit]
                      (binding [*uudelleen-kaynnistaja-mukaan?* true
                                *aloita-sonja?* true
                                *lisattavia-kuuntelijoita?* true
                                *sonja-kaynnistetty-fn* (fn []
                                                          (s-tk/sonja-jolokia-jono +tloik-ilmoitusviestijono+ nil :purge)
                                                          (s-tk/sonja-jolokia-jono +tloik-ilmoituskuittausjono+ nil :purge))]
                        (jarjestelma-fixture testit))))

;; Testaa:
; sonjan käynnistyksestä tulee ilmoitus
; sonja toimii oikein
; Sonja hajoaa -> käynnistetään uudestaan
; käynnistyksen aikana hoidetaan olemassa olevat palvelukutsut loppuun
; käynnistyksen jälkeen aletaan kuuntelemaan tapahtumia ihan normaalisti
; Sonjan kautta voi lähettää viestejä käynnistyksen jälkeen

(deftest sonjan-tapahtumia-kuunneellaan
  (ei-lisattavia-kuuntelijoita!)
  (odota-ehdon-tayttymista #(-> harja-tarkkailija :uudelleen-kaynnistaja :uudelleen-kaynnistaja/sonja-yhteys-aloitettu? deref true?)
                           "Uudelleen käynnistäjän pitäisi tietää Sonjan käynnistymisestä"
                           5000))
