(ns harja.palvelin.integraatiot.sampo-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.sampo :refer [->Sampo] :as sampo]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.jms :refer [feikki-sonja]]
            [harja.palvelin.komponentit.sonja :as sonja]
            [clojure.core.async :refer [<! >! go] :as async]
            [harja.xml :as xml]))

(def +lahetysjono+ "lahetysjono")
(def +kuittausjono+ "kuittausjono")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :sonja (feikki-sonja)
                        :sampo (component/using (->Sampo +lahetysjono+ +kuittausjono+) [:db :sonja])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(def +xsd-polku+ "test/xsd/sampo/outbound/")


(deftest yrita-laheta-maksuera-jota-ei-ole-olemassa
  (is (= {:virhe :maksueran-lukitseminen-epaonnistui} (sampo/laheta-maksuera-sampoon (:sampo jarjestelma) 666))))

(deftest laheta-maksuera
  (let [ch (async/chan)]
    (println jarjestelma)
    (sonja/kuuntele (:sonja jarjestelma) +lahetysjono+ #(async/put! ch (.getText %)))
    (println "ennen lähetystä")
    (is (sampo/laheta-maksuera-sampoon (:sampo jarjestelma) 1) "Lähetys onnistui")
    (println "lahetyksen jalkeen")
    (let [[sampoon-lahetetty-xml luettu-ch] (async/alts!! [ch (async/timeout 1000)])]
      (is (= luettu-ch ch) "Sampo lähetys ei mennyt kanavaan sekunnissa")
      (is (= (xml/validoi +xsd-polku+ "nikuxog_product.xsd" sampoon-lahetetty-xml))))))