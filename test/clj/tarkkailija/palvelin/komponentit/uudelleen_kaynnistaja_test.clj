(ns ^:integraatio tarkkailija.palvelin.komponentit.uudelleen-kaynnistaja-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
            [harja.integraatio :as integraatio]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtumien-tulkkaus]
            [harja.palvelin.asetukset :as a]
            [taoensso.timbre :as log]
            [harja.palvelin.tyokalut.jarjestelma :as jarjestelma]
            [harja.palvelin.integraatiot.jms :as jms]
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
            [harja.palvelin.tyokalut.tapahtuma-apurit :as event-apurit]
            [harja.pvm :as pvm]
            [clj-time
             [coerce :as tc]
             [format :as df]]
            [clojure.core.async :as async]))

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
                      (println "POIS KYTKETYT OMINAISUUDET")
                      (println @a/pois-kytketyt-ominaisuudet)
                      (swap! a/pois-kytketyt-ominaisuudet disj :sonja-uudelleen-kaynnistys)
                      (let [jarjestelma-restart-tarkkailija (atom nil)]
                        (binding [*uudelleen-kaynnistaja-mukaan?* true
                                  *aloita-sonja?* true
                                  *lisattavia-kuuntelijoita?* true
                                  *sonja-kaynnistetty-fn* (fn []
                                                            (s-tk/sonja-jolokia-jono +tloik-ilmoitusviestijono+ nil :purge)
                                                            (s-tk/sonja-jolokia-jono +tloik-ilmoituskuittausjono+ nil :purge))
                                  *kaynnistyksen-jalkeen-hook* (fn []
                                                                 (reset! jarjestelma-restart-tarkkailija
                                                                         (event-apurit/tarkkaile-tapahtumaa :harjajarjestelman-restart
                                                                                                            {}
                                                                                                            (fn [{:keys [palvelin payload]}]
                                                                                                              (println "HARJAJÄRJESTELMÄN RESTART")
                                                                                                              (println [palvelin payload])
                                                                                                              (when (= palvelin event-apurit/host-nimi)
                                                                                                                (alter-var-root #'jarjestelma
                                                                                                                                (fn [harja-jarjestelma]
                                                                                                                                  (log/debug "harjajarjestelman-restart")
                                                                                                                                  (try (let [uudelleen-kaynnistetty-jarjestelma (jarjestelma/system-restart harja-jarjestelma payload)]
                                                                                                                                         (if (jarjestelma/kaikki-ok? uudelleen-kaynnistetty-jarjestelma)
                                                                                                                                           (do (jms/aloita-sonja uudelleen-kaynnistetty-jarjestelma)
                                                                                                                                               (event-apurit/julkaise-tapahtuma :harjajarjestelman-restart-onnistui tapahtumien-tulkkaus/tyhja-arvo))
                                                                                                                                           (event-apurit/julkaise-tapahtuma :harjajarjestelman-restart-epaonnistui tapahtumien-tulkkaus/tyhja-arvo))
                                                                                                                                         uudelleen-kaynnistetty-jarjestelma)
                                                                                                                                       (catch Throwable t
                                                                                                                                         (log/error "Harjajärjestelmän uudelleen käynnistyksessä virhe: " (.getMessage t) ".\nStack: " (.printStackTrace t))
                                                                                                                                         (event-apurit/julkaise-tapahtuma :harjajarjestelman-restart-epaonnistui tapahtumien-tulkkaus/tyhja-arvo)
                                                                                                                                         nil))))))))
                                                                 (-> jarjestelma-restart-tarkkailija deref deref))
                                  *ennen-sulkemista-hook* (fn []
                                                            (event-apurit/lopeta-tapahtuman-kuuntelu (-> jarjestelma-restart-tarkkailija deref deref)))]
                          (jarjestelma-fixture testit)))
                      (swap! a/pois-kytketyt-ominaisuudet conj :sonja-uudelleen-kaynnistys)))

;; Testaa:
; sonjan käynnistyksestä tulee ilmoitus
; sonja toimii oikein
; Sonja hajoaa -> käynnistetään uudestaan
; käynnistyksen aikana hoidetaan olemassa olevat palvelukutsut loppuun
; käynnistyksen jälkeen aletaan kuuntelemaan tapahtumia ihan normaalisti
; Sonjan kautta voi lähettää viestejä käynnistyksen jälkeen

(deftest sonjan-tapahtumia-kuunneellaan
  (ei-lisattavia-kuuntelijoita!)
  (testing "sonja yhteys aloitettu viesti menee perille uudestaan käynnistäjälle"
    (odota-ehdon-tayttymista #(-> harja-tarkkailija :uudelleen-kaynnistaja ::uudelleen-kaynnistaja/sonja-yhteys-aloitettu? deref true?)
                             "Uudelleen käynnistäjän pitäisi tietää Sonjan käynnistymisestä"
                             10000))
  (testing "Sonja toimii oikein ja siitä menee viesti perille uudestaan käynnistäjälle"
    (odota-ehdon-tayttymista #(and (-> harja-tarkkailija :uudelleen-kaynnistaja ::uudelleen-kaynnistaja/viestien-maara-kaynnistyksesta deref (> 0))
                                   (-> harja-tarkkailija :uudelleen-kaynnistaja ::uudelleen-kaynnistaja/uudelleen-kaynnistyksia deref (= 0)))
                             "Uudelleen käynnistäjän pitäisi tietää Sonjan käynnistymisestä"
                             10000)))

(deftest sonjan-hajotessa-se-potkitaan-uudestaan-pystyyn
  (ei-lisattavia-kuuntelijoita!)
  (let [restart-onnistui-tarkkailija (event-apurit/tapahtuman-tarkkailija! :harjajarjestelman-restart-onnistui)
        restart-epaonnistui-tarkkailija (event-apurit/tapahtuman-tarkkailija! :harjajarjestelman-restart-epaonnistui)
        restart-onnistui-tarkkailija (async/<!! restart-onnistui-tarkkailija)
        restart-epaonnistui-tarkkailija (async/<!! restart-epaonnistui-tarkkailija)
        tila (atom nil)]
    (async/go (let [[arvo kanava] (async/alts! [restart-onnistui-tarkkailija
                                                restart-epaonnistui-tarkkailija
                                                (async/timeout 15000)])]
                (cond
                  (nil? arvo) (reset! tila :timeout)
                  (= kanava restart-onnistui-tarkkailija) (reset! tila :restart-onnistui)
                  (= kanava restart-epaonnistui-tarkkailija) (reset! tila :restart-epaonnistui))
                (event-apurit/lopeta-tapahtuman-kuuntelu restart-onnistui-tarkkailija)
                (event-apurit/lopeta-tapahtuman-kuuntelu restart-epaonnistui-tarkkailija)))
    (odota-ehdon-tayttymista #(-> harja-tarkkailija :uudelleen-kaynnistaja ::uudelleen-kaynnistaja/sonja-yhteys-aloitettu? deref true?)
                             "Uudelleen käynnistäjän pitäisi tietää Sonjan käynnistymisestä"
                             10000)
    (-> jarjestelma :sonja :tila deref :yhteys (.close))
    (odota-ehdon-tayttymista #(not (nil? @tila))
                             "Uudelleen käynnistäjän pitäisi aloittaa uudelleen käynnistys prosessi"
                             15000)
    (is (= @tila :restart-onnistui) "Uudelleen käynnistämisen pitäisi onnistua")))
