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
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuerat]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [taoensso.timbre :as log]))

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
    (sonja/kuuntele! (:sonja jarjestelma) +lahetysjono-ulos+ #(swap! viestit conj (.getText %)))
    (is (sampo/laheta-maksuera-sampoon (:sampo jarjestelma) +testi-maksueran-numero+) "Lähetys onnistui")
    (odota-ehdon-tayttymista #(= 2 (count @viestit)) "Sekä kustannussuunnitelma, että maksuerä on lähetetty." 10000)
    (let [sampoon-lahetetty-maksuera (first (filter #(not (.contains % "<CostPlans>")) @viestit))
          sampoon-lahetetty-kustannussuunnitelma (first (filter #(.contains % "<CostPlans>") @viestit))]
      (is (xml/validi-xml? +xsd-polku+ "nikuxog_product.xsd" sampoon-lahetetty-maksuera))
      (is (xml/validi-xml? +xsd-polku+ "nikuxog_costPlan.xsd" sampoon-lahetetty-kustannussuunnitelma)))))

(deftest maksueran-tietojen-haku
  (let [summa {:akillinen-hoitotyo 0.0M
               :yksikkohintainen 0.0M
               :sakko -3000.0M
               :muu 0.0M
               :indeksi nil
               :bonus 0.0M
               :lisatyo 100.29M
               :urakka_id 104
               :kokonaishintainen 424242.2M
               :tpi_id 666}
        hae-maksueranumero #(first (q-map "select numero, toimenpideinstanssi from maksuera where nimi = 'Oulu Talvihoito TP' and tyyppi = '" % "'"))]
    (let [maksuera (hae-maksueranumero "sakko")]
      (is (= -3000.0M
             (get-in (maksuerat/hae-maksueran-tiedot
                       (:db jarjestelma)
                       (:numero maksuera)
                       [(assoc summa :tpi_id (:toimenpideinstanssi maksuera))])
                     [:maksuera :summa]))
          "Sakko on laskettu oikein, kun sakon summa on negatiivinen jo aluksi")

      (is (= 0
             (get-in (maksuerat/hae-maksueran-tiedot
                       (:db jarjestelma)
                       (:numero maksuera)
                       [(assoc summa :tpi_id (:toimenpideinstanssi maksuera) :sakko nil)])
                     [:maksuera :summa]))
          "Sakko on laskettu oikein, kun sakon summa on nil"))))

(deftest kaikkien-maksueran-ja-kustannussuunnitelman-paivittaminen-likaiseksi
  (let [db (:db jarjestelma)
        tpi  (first (q-map "select id from toimenpideinstanssi where nimi = 'Oulu Talvihoito TP' "))]

    ;; Varmistetaan lähtötilanne: maksuerät ja kustannussuunnitelmat eivät ole likaisia.
    (is not(= [{:count 8}] (q-map (str "select count(*) from maksuera where toimenpideinstanssi = 1 AND likainen = TRUE" ))))
    (is not(= [{:count 8}] (q-map (str "select count(*) from kustannussuunnitelma where maksuera in (select numero from maksuera where toimenpideinstanssi = 1 )  AND likainen = TRUE"))))

    ;; Tarkistetaan ettei vanhentuneiden toimenpideinstanssien maksueriä ja kustannussuunnitelmia merkitä likaiseksi
    (maksuerat/paivita-toimenpiteen-maksuerat-ja-kustannussuunnitelmat-likaisiksi db (:id tpi))
    (is not(= [{:count 8}] (q-map (str "select count(*) from maksuera where toimenpideinstanssi = 1 AND likainen = TRUE" ))))
    (is not(= [{:count 8}] (q-map (str "select count(*) from kustannussuunnitelma where maksuera in (select numero from maksuera where toimenpideinstanssi = 1 )  AND likainen = TRUE"))))

    ;; Päivitetään toimenpideinstanssi voimaan ja testataan että voimassaolevan tpi:n maksuerät ja kustannussuunnitelmat päivittyvät
    (u "update toimenpideinstanssi set loppupvm = NOW() where nimi = 'Oulu Talvihoito TP' ")
    (maksuerat/paivita-toimenpiteen-maksuerat-ja-kustannussuunnitelmat-likaisiksi db (:id tpi))
    (is (= [{:count 8}] (q-map (str "select count(*) from maksuera where toimenpideinstanssi = 1 AND likainen = TRUE"))))
    (is (= [{:count 8}] (q-map (str "select count(*) from kustannussuunnitelma where maksuera in (select numero from maksuera where toimenpideinstanssi = 1 )  AND likainen = TRUE"))))))
