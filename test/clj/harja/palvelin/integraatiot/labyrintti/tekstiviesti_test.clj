(ns harja.palvelin.integraatiot.labyrintti.tekstiviesti-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.labyrintti.tekstiviesti :as tekstari]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]))

(def asetukset nil)

(def sanoma "source=+35844555666&text=V2%20Vastaanotto%20Hoidetaan%20homma%21")
(def sanoma-ilman-numeroa "text=L2%20Lopetus%20Lopetetaan%20koko%20homma%21")
(def sanoma-ilman-viestia "source=+35844555666")


(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    nil
    :tekstiviesti (component/using (tekstari/->Tekstiviesti asetukset) [:http-palvelin :db :integraatioloki ])))
(use-fixtures :once jarjestelma-fixture)

(deftest vastaanota-tekstiviesti-onnistuu
  (let [vastaus (api-tyokalut/post-kutsu ["/tekstiviesti/toimenpidekuittaus"] "livi" portti sanoma)]
    (is (= 200 (:status vastaus)))))
(deftest vastaanota-tekstiviesti-epaonnistuu-puhelinnumero-puuttuu
  (let [vastaus (api-tyokalut/post-kutsu ["/tekstiviesti/toimenpidekuittaus"] "livi" portti sanoma-ilman-numeroa)]
    (is (= 500 (:status vastaus)))))
(deftest vastaanota-tekstiviesti-epaonnistuu-viesti-puuttuu
  (let [vastaus (api-tyokalut/post-kutsu ["/tekstiviesti/toimenpidekuittaus"] "livi" portti sanoma-ilman-viestia)]
    (is (= 500 (:status vastaus)))))
