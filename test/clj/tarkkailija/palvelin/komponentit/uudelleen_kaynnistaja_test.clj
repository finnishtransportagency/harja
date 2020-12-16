(ns ^:integraatio tarkkailija.palvelin.komponentit.uudelleen-kaynnistaja-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
            [harja.integraatio :as integraatio]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtumien-tulkkaus]
            [harja.palvelin.asetukset :as a]
            [compojure.core :refer [PUT]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelu]]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [taoensso.timbre :as log]
            [harja.palvelin.tyokalut.jarjestelma :as jarjestelma]
            [harja.palvelin.integraatiot.jms :as jms]
            [tarkkailija.palvelin.komponentit.uudelleen-kaynnistaja :as uudelleen-kaynnistaja]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.jms-test :refer [feikki-jms]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.palvelin.integraatiot.labyrintti.sms :refer [->Labyrintti]]
            [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]
            [harja.palvelin.integraatiot.jms.tyokalut :as s-tk]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as event-apurit]
            [harja.palvelin.tyokalut.komponentti-protokollat :as kp]
            [harja.pvm :as pvm]
            [compojure.core :as compojure]
            [clj-time
             [coerce :as tc]
             [format :as df]]
            [clojure.core.async :as async]
            [cheshire.core :as cheshire]
            [clojure.string :as clj-str]))

(def kayttaja "skanska")

(defonce asetukset {:sonja (assoc integraatio/sonja-asetukset :julkaise-tila? true)})

(def koko-testin-tila (atom {}))

(defrecord TestiAPIKomponentti []
  component/Lifecycle
  (start
    [this]
    (julkaise-reitti
      (:http-palvelin this)
      :testiapikutsu
      (compojure/make-route
        :post
        "/api/testiapikutsu"
        (fn [request]
          (let [body (slurp (:body request))]
            (let [odota-restat? (get (cheshire/decode body true) :odota-restart)]
              (when odota-restat?
                (async/<!! (async/go-loop [kulunut-aika 0]
                             (when (and (not (:restart-done? @koko-testin-tila))
                                        (< kulunut-aika 20000))
                               (async/<! (async/timeout 100))
                               (recur (+ kulunut-aika 100))))))
              (swap! koko-testin-tila assoc :testiapikutsu-done? true))
            {:status 200
             :headers {"Content-Type" "application/json"}}))))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :testiapikutsu)
    this))

(defrecord TestiKuuntelijaKomponentti []
  component/Lifecycle
  (start [this]
    (assoc this :testikuuntelija (jms/kuuntele! (:sonja this) +tloik-ilmoituskuittausjono+ (fn [viesti] (swap! koko-testin-tila update :tloik-ilmoituksia-n (fn [n] (inc (or n 0))))))))
  (stop [this]
    ((:testikuuntelija this))
    (dissoc this :testikuuntelija)))

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
             [:db :sonja :integraatioloki :labyrintti :sonja-sahkoposti])
    :testiapi (component/using
                    (->TestiAPIKomponentti)
                    [:http-palvelin])
    :testikuuntelija (component/using
                       (->TestiKuuntelijaKomponentti)
                       [:sonja :tloik])))

(use-fixtures :each (fn [testit]
                      (println "POIS KYTKETYT OMINAISUUDET")
                      (println @a/pois-kytketyt-ominaisuudet)
                      (swap! a/pois-kytketyt-ominaisuudet disj :sonja-uudelleen-kaynnistys)
                      (reset! koko-testin-tila {})
                      (let [jarjestelma-restart-tarkkailija (atom nil)]
                        (binding [*uudelleen-kaynnistaja-mukaan?* true
                                  *aloitettavat-jmst* #{"sonja"}
                                  *lisattavia-kuuntelijoita?* true
                                  *jms-kaynnistetty-fn* (fn []
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
                                                                                                                                         (jms/aloita-jms (:sonja uudelleen-kaynnistetty-jarjestelma))
                                                                                                                                         (jms/aloita-jms (:itmf uudelleen-kaynnistetty-jarjestelma))
                                                                                                                                         (if (jarjestelma/kaikki-ok? uudelleen-kaynnistetty-jarjestelma (* 1000 10))
                                                                                                                                           (event-apurit/julkaise-tapahtuma :harjajarjestelman-restart-onnistui tapahtumien-tulkkaus/tyhja-arvo)
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
; käynnistyksen jälkeen aletaan kuuntelemaan tapahtumia ihan normaalisti
; Sonjan kautta voi lähettää viestejä käynnistyksen jälkeen

(deftest sonjan-tapahtumia-kuunneellaan
  (ei-lisattavia-kuuntelijoita!)
  (testing "sonja yhteys aloitettu viesti menee perille uudestaan käynnistäjälle"
    (odota-ehdon-tayttymista #(-> harja-tarkkailija :uudelleen-kaynnistaja ::uudelleen-kaynnistaja/sonja-yhteys-aloitettu-atom? deref true?)
                             "Uudelleen käynnistäjän pitäisi tietää Sonjan käynnistymisestä"
                             10000))
  (testing "Sonja toimii oikein ja siitä menee viesti perille uudestaan käynnistäjälle"
    (odota-ehdon-tayttymista #(and (-> harja-tarkkailija :uudelleen-kaynnistaja ::uudelleen-kaynnistaja/viestien-maara-kaynnistyksesta deref (> 0))
                                   (-> harja-tarkkailija :uudelleen-kaynnistaja ::uudelleen-kaynnistaja/uudelleen-kaynnistyksia deref (= 0)))
                             "Uudelleen käynnistäjän pitäisi tietää Sonjan käynnistymisestä"
                             10000)))

(deftest sonjan-hajotessa-se-potkitaan-uudestaan-pystyyn
  (ei-lisattavia-kuuntelijoita!)
  ;(lisaa-kuuntelijoita! {+tloik-ilmoituskuittausjono+ (fn [viesti] (swap! koko-testin-tila update :tloik-ilmoituksia-n (fn [n] (inc (or n 0)))))})
  (let [restart-onnistui-tarkkailija (event-apurit/tapahtuman-tarkkailija! :harjajarjestelman-restart-onnistui)
        restart-epaonnistui-tarkkailija (event-apurit/tapahtuman-tarkkailija! :harjajarjestelman-restart-epaonnistui)
        restart-onnistui-tarkkailija (async/<!! restart-onnistui-tarkkailija)
        restart-epaonnistui-tarkkailija (async/<!! restart-epaonnistui-tarkkailija)
        tila (atom nil)]
    (jms/laheta (:sonja jarjestelma) +tloik-ilmoitusviestijono+ (testi-ilmoitus-sanoma))
    (odota-ehdon-tayttymista #(= 1 (get @koko-testin-tila :tloik-ilmoituksia-n)) "Kuittaus on vastaanotettu." 20000)
    (is (= 1 (get @koko-testin-tila :tloik-ilmoituksia-n)))
    (testing "sonja uudelleen käynnistys triggeröity yhteyden sammutuksesta ja http kutsu menee läpi"
      (api-tyokalut/async-post-kutsu ["/api/testiapikutsu"] kayttaja portti "{\"odota-restart\": true}" (fn [& args]))
      (async/go (let [[arvo kanava] (async/alts! [restart-onnistui-tarkkailija
                                                  restart-epaonnistui-tarkkailija
                                                  (async/timeout 15000)])]
                  (cond
                    (nil? arvo) (reset! tila :timeout)
                    (= kanava restart-onnistui-tarkkailija) (reset! tila :restart-onnistui)
                    (= kanava restart-epaonnistui-tarkkailija) (reset! tila :restart-epaonnistui))
                  (swap! koko-testin-tila assoc :restart-done? true)
                  (event-apurit/lopeta-tapahtuman-kuuntelu restart-onnistui-tarkkailija)
                  (event-apurit/lopeta-tapahtuman-kuuntelu restart-epaonnistui-tarkkailija)))
      (odota-ehdon-tayttymista #(-> harja-tarkkailija :uudelleen-kaynnistaja ::uudelleen-kaynnistaja/sonja-yhteys-aloitettu-atom? deref true?)
                               "Uudelleen käynnistäjän pitäisi tietää Sonjan käynnistymisestä"
                               10000)
      (-> jarjestelma :sonja :tila deref :yhteys (.close))
      (odota-ehdon-tayttymista #(not (nil? @tila))
                               "Uudelleen käynnistäjän pitäisi aloittaa uudelleen käynnistys prosessi"
                               15000)
      (is (= @tila :restart-onnistui) "Uudelleen käynnistämisen pitäisi onnistua")
      (odota-ehdon-tayttymista #(get @koko-testin-tila :testiapikutsu-done?)
                               "API kutsu pitäisi mennä sonjarestartista huolimatta läpi"
                               5000))
    (testing "uusi ilmoitus pitäsi toimia restartin jälkeen"
      (odota-ehdon-tayttymista #(kp/status-ok? (:tloik jarjestelma))
                               (str "Tloik ei ole ok odotuksen jälkeen: " (kp/status (:tloik jarjestelma)))
                               20000)
      (jms/laheta (:sonja jarjestelma) +tloik-ilmoitusviestijono+ (-> (testi-ilmoitus-sanoma)
                                                                        (clj-str/replace "<ilmoitusId>123456789</ilmoitusId>" "<ilmoitusId>123456788</ilmoitusId>")
                                                                        (clj-str/replace "<tunniste>UV-1509-1a</tunniste>" "<tunniste>UV-1509-2a</tunniste>")))
      (odota-ehdon-tayttymista #(= 2 (get @koko-testin-tila :tloik-ilmoituksia-n)) "Kuittaus on vastaanotettu restartin jälkeen." 5000)
      (is (= 2 (get @koko-testin-tila :tloik-ilmoituksia-n))))))
