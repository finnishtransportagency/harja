(ns harja.palvelin.integraatiot.vayla-rest.sampo-api-vienti-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [harja.integraatio :as integraatio]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as integraatiopiste-http]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.vayla-rest.sampo-api :as sampo-api]))

(def kayttaja "destia")
(def kayttaja-yit "yit-rakennus")
(def +xsd-polku+ "xsd/sampo/inbound/")
(def +testi-maksueran-numero+ 1)

(defonce asetukset integraatio/api-sampo-asetukset)

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-sampo (component/using
                 (sampo-api/->ApiSampo asetukset)
                 [:http-palvelin :db :integraatioloki])))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

;; Helpperit
(def muodosta-lahetys-url
  (str (get-in asetukset [:sampo-api :palvelin])
    (get-in asetukset [:sampo-api :lahetys-url])))

(def onnistunut-maksuera-kuittaus
  (slurp "test/resurssit/sampo/maksuera_ack.xml"))

(def onnistunut-kustannussuunnitelma-kuittaus
  (slurp "test/resurssit/sampo/maksuera_ack.xml"))

(def epaonnistunut-maksuera-kuittaus
  (slurp "test/resurssit/sampo/maksuera_nack.xml"))

(defn hae-maksueran-tiedot [numero]
  (first (q-map (str (format "select numero, luotu, muokattu, lahetetty, nimi, tyyppi, tila
  FROM maksuera WHERE numero = %s" numero)))))

(defn hae-kustannussuunnitelman-tiedot [numero]
  (first (q-map (str (format "SELECT maksuera, luotu, muokattu, lahetetty, tila FROM kustannussuunnitelma WHERE maksuera = %s" numero)))))

(defn hae-integraatiotapahtumat-tietokannasta []
  (q-map (str "SELECT it.id, it.integraatio, it.alkanut, it.paattynyt, it.onnistunut, i.jarjestelma, i.nimi
            FROM integraatiotapahtuma it JOIN integraatio i ON i.id = it.integraatio order by it.id DESC;")))

;; Testit
(deftest yrita-laheta-maksuera-jota-ei-ole-olemassa
  (is (thrown? Exception (sampo-api/laheta-maksuera-sampoon (:api-sampo jarjestelma) 777)) "Tuntematon maksuerä jäi kiinni"))

(deftest laheta-maksuera-api-onnistuu-test
  (let [maksueran-tiedot-alkuun (hae-maksueran-tiedot +testi-maksueran-numero+)
        ksuun-tiedot-alkuun (hae-kustannussuunnitelman-tiedot +testi-maksueran-numero+)
        integ-tapahtumat-alkuun (hae-integraatiotapahtumat-tietokannasta)
        vastaus (with-redefs [integraatiopiste-http/tee-http-kutsu (fn [_ _ _ _ _ _ _ _ kutsudata _ _]
                                                                     {:status 200
                                                                      :header "jotain"
                                                                      :body (if (str/includes? kutsudata "costPlan")
                                                                              onnistunut-kustannussuunnitelma-kuittaus
                                                                              onnistunut-maksuera-kuittaus
                                                                              )})]
                  (sampo-api/laheta-maksuera-sampoon (:api-sampo jarjestelma) +testi-maksueran-numero+))
        maksueran-tiedot-lopuksi (hae-maksueran-tiedot +testi-maksueran-numero+)
        ksuun-tiedot-lopuksi (hae-kustannussuunnitelman-tiedot +testi-maksueran-numero+)
        integ-tapahtumat-loppuun (hae-integraatiotapahtumat-tietokannasta)]

    ;; Varmistetaan, että maksuerän status muuttuu tietokannassa oikein
    (is (nil? (:lahetetty maksueran-tiedot-alkuun)))
    (is (not (nil? (:lahetetty maksueran-tiedot-lopuksi))))

    ;; Varmistetaan, että kustannussuunnitelman status muuttuu tietokannassa oikein
    (is (nil? (:lahetetty ksuun-tiedot-alkuun)))
    (is (not (nil? (:lahetetty ksuun-tiedot-lopuksi))))


    ;; Varmistetaan, että integraatiologilla tilanne päivittyy oikein
    (is (empty? integ-tapahtumat-alkuun))
    (is (= 2 (count integ-tapahtumat-loppuun)))
    (is (= "maksuera-lahetys" (:nimi (first integ-tapahtumat-loppuun))))
    (is (= true (:onnistunut (first integ-tapahtumat-loppuun))))
    (is (= "kustannussuunnitelma-lahetys" (:nimi (second integ-tapahtumat-loppuun))))
    (is (= true (:onnistunut (second integ-tapahtumat-loppuun))))))

(deftest laheta-likainen-maksuera-api-ei-onnistu-test
  (let [likainen-maksueranumero 33                            ;(first (str "select numero from maksuera where likainen = true limit 1"))
        maksueran-tiedot-alkuun (hae-maksueran-tiedot likainen-maksueranumero)
        ksuun-tiedot-alkuun (hae-kustannussuunnitelman-tiedot likainen-maksueranumero)
        integ-tapahtumat-alkuun (hae-integraatiotapahtumat-tietokannasta)
        vastaus (with-redefs [integraatiopiste-http/tee-http-kutsu (fn [_ _ _ _ _ _ _ _ kutsudata _ _]
                                                                     {:status 200
                                                                      :header "jotain"
                                                                      :body (if (str/includes? kutsudata "CostPlan")
                                                                              onnistunut-kustannussuunnitelma-kuittaus
                                                                              epaonnistunut-maksuera-kuittaus
                                                                              )})]
                  (sampo-api/laheta-maksuera-sampoon (:api-sampo jarjestelma) likainen-maksueranumero))
        maksueran-tiedot-lopuksi (hae-maksueran-tiedot likainen-maksueranumero)
        ksuun-tiedot-lopuksi (hae-kustannussuunnitelman-tiedot likainen-maksueranumero)
        integ-tapahtumat-loppuun (hae-integraatiotapahtumat-tietokannasta)]

    ;; Varmistetaan, että maksuerän status muuttuu tietokannassa oikein
    (is (nil? (:lahetetty maksueran-tiedot-alkuun)))
    (is (nil? (:lahetetty maksueran-tiedot-lopuksi)))
    (is (= "virhe" (:tila maksueran-tiedot-lopuksi)))

    ;; Varmistetaan, että kustannussuunnitelman status muuttuu tietokannassa oikein
    (is (nil? (:lahetetty ksuun-tiedot-alkuun)))
    (is (not (nil? (:lahetetty ksuun-tiedot-lopuksi))))


    ;; Varmistetaan, että integraatiologilla tilanne päivittyy oikein
    (is (empty? integ-tapahtumat-alkuun))
    (is (not (empty? integ-tapahtumat-loppuun)))))
