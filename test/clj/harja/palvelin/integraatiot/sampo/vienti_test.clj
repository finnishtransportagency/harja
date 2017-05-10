(ns harja.palvelin.integraatiot.sampo.vienti-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.sampo.sampo-komponentti :refer [->Sampo] :as sampo]
            [harja.palvelin.integraatiot.integraatioloki :refer [->Integraatioloki]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.vienti :as sampo-vienti]
            [harja.kyselyt.maksuerat :as qm]
            [harja.kyselyt.kustannussuunnitelmat :as qk]
            [harja.tyokalut.xml :as xml]))

(def +lahetysjono-sisaan+ "lahetysjono-sisaan")
(def +kuittausjono-sisaan+ "kuittausjono-sisaan")
(def +lahetysjono-ulos+ "lahetysjono-ulos")
(def +kuittausjono-ulos+ "kuittausjono-ulos")

(def +testi-maksueran-numero+ 1)

(def +xsd-polku+ "xsd/sampo/outbound/")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :sonja (feikki-sonja)
                        :integraatioloki (component/using (->Integraatioloki nil) [:db])
                        :sampo (component/using (->Sampo +lahetysjono-sisaan+ +kuittausjono-sisaan+ +lahetysjono-ulos+ +kuittausjono-ulos+ nil) [:db :sonja :integraatioloki])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest yrita-laheta-maksuera-jota-ei-ole-olemassa
  (is (thrown? Exception (sampo/laheta-maksuera-sampoon (:sampo jarjestelma) 666)) "Tuntematon maksuerä jäi kiinni"))

(deftest laheta-maksuera
  (let [viestit (atom [])]
    (sonja/kuuntele (:sonja jarjestelma) +lahetysjono-ulos+ #(swap! viestit conj (.getText %)))
    (is (sampo/laheta-maksuera-sampoon (:sampo jarjestelma) +testi-maksueran-numero+) "Lähetys onnistui")
    (odota-ehdon-tayttymista #(= 2 (count @viestit)) "Sekä kustannussuunnitelma, että maksuerä on lähetetty." 10000)
    (let [sampoon-lahetetty-maksuera (first (filter #(not (.contains % "<CostPlans>")) @viestit))
          sampoon-lahetetty-kustannussuunnitelma (first (filter #(.contains % "<CostPlans>") @viestit))]
      (is (xml/validi-xml? +xsd-polku+ "nikuxog_product.xsd" sampoon-lahetetty-maksuera))
      (is (xml/validi-xml? +xsd-polku+ "nikuxog_costPlan.xsd" sampoon-lahetetty-kustannussuunnitelma)))))

(deftest lahetettavat-maksuerat
  (with-redefs [qm/hae-likaiset-maksuerat (constantly (list {:numero +testi-maksueran-numero+
                                                             :urakkaid 104
                                                             :tpi_id +testi-maksueran-numero+}))
                qk/hae-likaiset-kustannussuunnitelmat (constantly (list {:maksuera +testi-maksueran-numero+
                                                                         :urakkaid 104
                                                                         :tpi_id +testi-maksueran-numero+}))
                qm/hae-urakan-maksuerien-summat (constantly (list {:akillinen-hoitotyo 0.0M
                                                                   :yksikkohintainen 0.0M
                                                                   :sakko -3000.0M
                                                                   :muu 0.0M
                                                                   :indeksi nil
                                                                   :bonus 0.0M
                                                                   :lisatyo 100.29M
                                                                   :urakka_id 104
                                                                   :kokonaishintainen 424242.2M
                                                                   :tpi_id +testi-maksueran-numero+}))]
    (let [viestit (atom [])]
      (sonja/kuuntele (:sonja jarjestelma) +lahetysjono-ulos+ #(swap! viestit conj (.getText %)))

      (sampo-vienti/aja-paivittainen-lahetys (:sonja jarjestelma) (:integraatioloki jarjestelma) (:db jarjestelma) +lahetysjono-ulos+)
      (odota-ehdon-tayttymista #(= 2 (count @viestit)) "Sekä kustannussuunnitelma
että maksuerä on lähetetty." 10000)
      (println "HÖHÖÖ " (pr-str @viestit))
      (is (= "424242.2"
             (-> @viestit
                 first
                 .getBytes
                 clojure.java.io/input-stream
                 parse
                 xml-zip
                 (z/xml1-> :Products :Product :CustomInformation :instance :CustomInformation :ColumnValue (z/attr= :name "vv_paym_sum") z/text)))))))
