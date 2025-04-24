(ns harja.palvelin.integraatiot.labyrintti.tekstiviesti-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.labyrintti.tekstiviesti :as tekstiviesti]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]))

(def asetukset nil)

(def +testi-sms-url+ "harja.testi.sms")
(def sanoma "source=+35844555666&text=V2%20Vastaanotto%20Hoidetaan%20homma%21")
(def sanoma-ilman-numeroa "text=L2%20Lopetus%20Lopetetaan%20koko%20homma%21")
(def sanoma-ilman-viestia "source=+35844555666")


(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    nil
    :tekstiviesti (component/using (tekstiviesti/luo-tekstiviesti-komponentti
                                     {:url +testi-sms-url+ :apiavain "testiapiavain"})
                    [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tekstiviestin-lahetys
  (with-fake-http
    [+testi-sms-url+ "ok"]
    (let [vastaus (tekstiviesti/laheta (:labyrintti jarjestelma) "0987654321" "Testi" {"X-Correlation-ID" 1234567})]
      (is (= "ok" (:sisalto vastaus))))))

(deftest tekstiviestin-epaonnistunut-lahetys
  (with-fake-http
    [+testi-sms-url+ "TESTI ERROR 2 1 message failed: Invalid phone number"]
    (is (thrown? Exception (tekstiviesti/laheta (:labyrintti jarjestelma) "0987654321" "Testi" {"X-Correlation-ID" 1234568}))
      "Poikkeusta ei heitetty virhe responsesta.")))


(deftest vastaanota-tekstiviesti-onnistuu
  (let [vastaus (api-tyokalut/post-kutsu ["/tekstiviesti/toimenpidekuittaus"] "livi" portti sanoma)]
    (is (= 200 (:status vastaus)))))
(deftest vastaanota-tekstiviesti-epaonnistuu-puhelinnumero-puuttuu
  (let [vastaus (api-tyokalut/post-kutsu ["/tekstiviesti/toimenpidekuittaus"] "livi" portti sanoma-ilman-numeroa)]
    (is (= 500 (:status vastaus)))))
(deftest vastaanota-tekstiviesti-epaonnistuu-viesti-puuttuu
  (let [vastaus (api-tyokalut/post-kutsu ["/tekstiviesti/toimenpidekuittaus"] "livi" portti sanoma-ilman-viestia)]
    (is (= 500 (:status vastaus)))))
