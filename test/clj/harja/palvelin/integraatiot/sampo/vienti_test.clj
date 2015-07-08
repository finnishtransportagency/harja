(ns harja.palvelin.integraatiot.sampo.vienti-test
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
            [harja.tyokalut.xml :as xml]))

(def +lahetysjono-sisaan+ "lahetysjono-sisaan")
(def +kuittausjono-sisaan+ "kuittausjono-sisaan")
(def +lahetysjono-ulos+ "lahetysjono-ulos")
(def +kuittausjono-ulos+ "kuittausjono-ulos")

(def +xsd-polku+ "resources/xsd/sampo/outbound/")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :sonja (feikki-sonja)
                        :sampo (component/using (->Sampo +lahetysjono-sisaan+ +kuittausjono-sisaan+ +lahetysjono-ulos+ +kuittausjono-ulos+ nil) [:db :sonja])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest yrita-laheta-maksuera-jota-ei-ole-olemassa
  (is (= {:virhe :maksueran-lukitseminen-epaonnistui} (:maksuera (sampo/laheta-maksuera-sampoon (:sampo jarjestelma) 666)))))

(deftest laheta-maksuera
  (let [viestit (atom [])]
    (println jarjestelma)
    (sonja/kuuntele (:sonja jarjestelma) +lahetysjono-ulos+ #(swap! viestit conj (.getText %)))
    (is (sampo/laheta-maksuera-sampoon (:sampo jarjestelma) 1) "Lähetys onnistui")
    (odota #(= 2 (count @viestit)) "Sekä kustannussuunnitelma, että maksuerä on lähetetty." 1000)
    (let [sampoon-lahetetty-maksuera (first (filter #(not (.contains % "<CostPlans>")) @viestit))
          sampoon-lahetetty-kustannussuunnitelma (first (filter #(.contains % "<CostPlans>") @viestit))]
      (is (xml/validoi +xsd-polku+ "nikuxog_product.xsd" sampoon-lahetetty-maksuera))
      (is (xml/validoi +xsd-polku+ "nikuxog_costPlan.xsd" sampoon-lahetetty-kustannussuunnitelma))))
  (u "UPDATE maksuera SET tila = NULL WHERE numero=1")
  (u "UPDATE kustannussuunnitelma SET tila = NULL WHERE maksuera=1"))
