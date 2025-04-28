(ns harja.palvelin.integraatiot.labyrintti.tekstiviesti-test
  (:require [cheshire.core :as cheshire]
            [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.tyokalut.json-validointi :as json]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.labyrintti.tekstiviesti :as sms]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]))

(def asetukset nil)

(def +sms-laheta-schema+ "json/tekstiviesti/tekstiviesti-laheta.schema.json")

(def +testi-sms-url+ "harja.testi.sms")

(def jarjestema-fixture
  (laajenna-integraatiojarjestelmafixturea
    nil
    :sms-vanha (component/using (sms/luo-tekstiviesti-komponentti
                                  {:url +testi-sms-url+ :apiavain "testiapiavain"}
                                  {:sms-url +testi-sms-url+ :apiavain "testiapiavain"})
                 [:http-palvelin :db :integraatioloki])
    :sms (component/using (sms/luo-tekstiviesti-komponentti
                            ;; Uusi SMS-integraatio aktiivinen ja korvaa siten vanhan käytössä
                            {:url +testi-sms-url+ :apiavain "testiapiavain" :aktiivinen? true}
                            {:sms-url +testi-sms-url+ :apiavain "testiapiavain"})
           [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestema-fixture)


;; -- Testaa uuden SMS-integraation lähetystä --
(deftest tekstiviestin-lahetys
  (let [lahetetty-payload (atom nil)]
    (with-fake-http
      [+testi-sms-url+ (fn [_ opts _]
                         (reset! lahetetty-payload (:body opts))
                         ;; TODO: Feikkaa oikea vastaus-payload
                         "ok")]
      (let [vastaus (tekstiviesti/laheta (:sms jarjestelma) "0987654321" "Testi" "1234567" {})]
        (is (nil? (json/validoi +sms-laheta-schema+ (cheshire/encode @lahetetty-payload))))
        (is (= "ok" (:sisalto vastaus)))))))

(deftest tekstiviestin-epaonnistunut-lahetys
  (with-fake-http
    [+testi-sms-url+ "TESTI ERROR 2 1 message failed: Invalid phone number"]
    (is (thrown? Exception (tekstiviesti/laheta (:sms jarjestelma) "0987654321" "Testi" 1234568 {}))
      "Poikkeusta ei heitetty virhe responsesta.")))

;; -- Vanhan LinkMobility SMS-integraation lähetystestit (Uusi integraatio ei ole aktiivinen) --
(deftest linkmobility-tekstiviestin-lahetys
  (let [lahetetty-payload (atom nil)]
    (with-fake-http
      [+testi-sms-url+ (fn [_ opts _]
                         (reset! lahetetty-payload {:parametrit (:form-params opts)
                                                    :otsikot (:headers opts)})
                         "ok")]
      (let [vastaus (tekstiviesti/laheta (:sms-vanha jarjestelma) "0987654321" "Testi" "1234567" {})]
        (is (= (:parametrit @lahetetty-payload) {"dests" "0987654321"
                                                 "text" "Testi"}))
        (is (= (select-keys (:otsikot @lahetetty-payload) ["X-Correlation-ID" "Content-Type"])
              {"X-Correlation-ID" "1234567"
               "Content-Type" "application/x-www-form-urlencoded"})
          (is (= "ok" (:sisalto vastaus))))))))

(deftest linkmobility-tekstiviestin-epaonnistunut-lahetys
  (with-fake-http
    [+testi-sms-url+ "TESTI ERROR 2 1 message failed: Invalid phone number"]
    (is (thrown? Exception (tekstiviesti/laheta (:sms-vanha jarjestelma) "0987654321" "Testi" "1234568" {}))
      "Poikkeusta ei heitetty virhe responsesta.")))


;; -- Vanhan LinkMobilty SMS-integraation vastaanottotestit --
(def linkmobility-sanoma "source=+35844555666&text=V2%20Vastaanotto%20Hoidetaan%20homma%21")
(def linkmobility-sanoma-ilman-numeroa "text=L2%20Lopetus%20Lopetetaan%20koko%20homma%21")
(def linkmobility-sanoma-ilman-viestia "source=+35844555666")

(deftest linkmobility-vastaanota-tekstiviesti-onnistuu
  (let [vastaus (api-tyokalut/post-kutsu ["/tekstiviesti/toimenpidekuittaus"] "livi" portti linkmobility-sanoma)]
    (is (= 200 (:status vastaus)))))
(deftest linkmobility-vastaanota-tekstiviesti-epaonnistuu-puhelinnumero-puuttuu
  (let [vastaus (api-tyokalut/post-kutsu ["/tekstiviesti/toimenpidekuittaus"] "livi" portti linkmobility-sanoma-ilman-numeroa)]
    (is (= 500 (:status vastaus)))))
(deftest linkmobility-vastaanota-tekstiviesti-epaonnistuu-viesti-puuttuu
  (let [vastaus (api-tyokalut/post-kutsu ["/tekstiviesti/toimenpidekuittaus"] "livi" portti linkmobility-sanoma-ilman-viestia)]
    (is (= 500 (:status vastaus)))))
