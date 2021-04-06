(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet :as paikkauskohteet]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-excel :as p-excel]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [dk.ative.docjure.spreadsheet :as xls]
            [clojure.java.io :as io]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :paikkauskohteet (component/using
                                           (paikkauskohteet/->Paikkauskohteet)
                                           [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(def default-paikkauskohde {:nimi "testinimi"
                            :alkupvm (pvm/->pvm "01.01.2020")
                            :loppupvm (pvm/->pvm "01.02.2020")
                            :paikkauskohteen-tila "valmis"
                            :tie 22
                            :aosa 1
                            :losa 2
                            :aet 10
                            :let 20
                            :yksikko "jm"
                            :suunniteltu-hinta 1000.00
                            :suunniteltu-maara 100
                            :tyomenetelma "UREM"})

(deftest paikkauskohteet-urakalle-testi
  (let [_ (hae-kemin-alueurakan-2019-2023-id)
        urakka-id @kemin-alueurakan-2019-2023-id
        paikkauskohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :paikkauskohteet-urakalle
                                        +kayttaja-jvh+
                                        {:urakka-id urakka-id})]
    (is (> (count paikkauskohteet) 0))))

;; Testataan käyttäjää, jolla ei ole oikeutta mihinkään
(deftest paikkauskohteet-urakalle-seppo-testi
  (let [_ (hae-kemin-alueurakan-2019-2023-id)
        urakka-id @kemin-alueurakan-2019-2023-id]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :paikkauskohteet-urakalle
                                           +kayttaja-seppo+
                                           {:urakka-id urakka-id}))
        "Poikkeusta ei heitetty! Sepolla olikin oikeus hakea paikkauskohteet.")))

;; Haetaan paikkauskohteet käyttäjälle, jolla ei ole oikeutta nähdä hintatietoja (urakan laadunvalvoja)
(deftest paikkauskohteet-ilman-kustannustietoja-testi
  (let [_ (hae-kemin-alueurakan-2019-2023-id)
        urakka-id @kemin-alueurakan-2019-2023-id
        ei-kustannustietoja-kayttaja (kemin-alueurakan-2019-2023-laadunvalvoja)
        paikkauskohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :paikkauskohteet-urakalle
                                        ei-kustannustietoja-kayttaja
                                        {:urakka-id urakka-id})]
    ;; Kustannustietoja ei saa löytyä haetuista poikkauskohteista
    (is (false? (contains? (first paikkauskohteet) :suunniteltu-hinta)))
    (is (false? (contains? (first paikkauskohteet) :toteutunut-hinta)))))

(deftest muokkaa-paikkauskohdetta-testi
  (let [_ (hae-kemin-alueurakan-2019-2023-id)
        urakka-id @kemin-alueurakan-2019-2023-id
        paikkauskohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :paikkauskohteet-urakalle
                                        +kayttaja-jvh+
                                        {:urakka-id urakka-id})
        ;; Otetaan eka
        kohde (first (filter #(= "Muokattava testikohde" (:nimi %)) paikkauskohteet))
        ;; Muokataan nimi, tierekisteriosoite, alkuaika, loppuaika, tila
        kohde (-> kohde
                  (assoc :nimi "testinimi")
                  (assoc :tie "22")
                  (assoc :alkupvm (pvm/->pvm "01.01.2020"))
                  (assoc :loppupvm (pvm/->pvm "01.02.2020"))
                  #_(assoc :paikkauskohteen-tila "valmis"))
        muokattu-kohde (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-paikkauskohde-urakalle
                                       +kayttaja-jvh+
                                       kohde)]
    (is (= muokattu-kohde kohde))))

(deftest luo-uusi-paikkauskohde-testi
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        kohde (merge {:urakka-id urakka-id}
                     default-paikkauskohde)

        kohde-id (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-paikkauskohde-urakalle
                                 +kayttaja-jvh+
                                 kohde)
        paikkauskohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :paikkauskohteet-urakalle
                                        +kayttaja-jvh+
                                        {:urakka-id urakka-id})]
    (is (> (count paikkauskohteet) 3))))

(deftest luo-uusi-paikkauskohde-virheellisin-tiedoin-testi
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        ;; Poistetaan kohteelta nimi
        kohde (dissoc (merge {:urakka-id urakka-id} default-paikkauskohde)
                      :nimi)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-paikkauskohde-urakalle
                                           +kayttaja-jvh+
                                           kohde))
        "Poikkeusta ei heitetty! Tallennus onnistui vaikka ei oli saanut")))

;; Testataan poistamisen toimivuutta ihan vain pääkäyttäjällä.
(deftest poista-paikkauskohde-paakayttajana-testi
  (let [;; Lisätään defaulta paikkauskohteelle haluamamme urakka-id
        ;; ja nimi vaihdetaan sellaiseksi, että helppo tunnistaa, onko poisto onnistunut
        nimi "Tämä kemin kohde tulee poistumaan"
        urakka-id @kemin-alueurakan-2019-2023-id
        kohde (merge default-paikkauskohde
                     {:urakka-id urakka-id
                      :nimi nimi})
        ;; Luodaan paikkauskohde tietokantaan
        kohde-id (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-paikkauskohde-urakalle
                                 +kayttaja-jvh+
                                 kohde)
        kohde (merge {:id (:id kohde-id)}
                     kohde)
        ;; Poistetaan paikkauskohde
        poistettu-kohde (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :poista-paikkauskohde
                                        +kayttaja-jvh+
                                        kohde)
        ;; Haetaan kohteet ja tarkistetaan, että onko meidän juuri luotu kohde enää listassa
        paikkauskohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :paikkauskohteet-urakalle
                                        +kayttaja-jvh+
                                        {:urakka-id urakka-id})]
    (is (= 0 (count (reduce (fn [loydetyt kohde]
                              (if (= nimi (:nimi kohde))
                                (conj loydetyt kohde)
                                loydetyt))
                            []
                            paikkauskohteet)))
        "Poistaminen ei onnistunut!")))


;; Testataan poistamisen toimivuutta väärillä käyttöoikeuksilla
(deftest poista-paikkauskohde-vaaralla-kayttajalla-ja-tiedoilla-testi
  (let [;; Lisätään defaulta paikkauskohteelle haluamamme urakka-id
        ;; ja nimi vaihdetaan sellaiseksi, että helppo tunnistaa, onko poisto onnistunut
        nimi "Tämä kemin kohde tulee poistumaan"
        urakka-id @kemin-alueurakan-2019-2023-id
        kohde (merge default-paikkauskohde
                     {:urakka-id urakka-id
                      :nimi nimi})
        ;; Luodaan paikkauskohde tietokantaan
        kohde-id (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-paikkauskohde-urakalle
                                 +kayttaja-jvh+
                                 kohde)
        kohde (merge {:id (:id kohde-id)}
                     kohde)
        ;; Tehdään myös aivan väärä kohde ja yritetään poistaa
        vaara-id 934534534
        vaara-kohde (merge {:id vaara-id}
                           kohde)
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :poista-paikkauskohde
                          +kayttaja-jvh+
                          vaara-kohde)
        ]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :poista-paikkauskohde
                                           +kayttaja-seppo+
                                           kohde))
        "Poikkeusta ei heitetty! Sepolla olikin oikeus poistaa kohde.")
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :poista-paikkauskohde
                                           +kayttaja-jvh+
                                           vaara-kohde))
        "Poikkeusta ei heitetty! Väärä paikkauskohde onnistuttiin tuhoamaan!")))

(defn- paivita-paikkaukkohteen-tila [kohde uusi-tila kayttaja]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-paikkauskohde-urakalle
                  kayttaja
                  (merge kohde {:paikkauskohteen-tila uusi-tila})))

(deftest paikkauskohde-tilamuutokset-testi
  (let [urakoitsija (kemin-alueurakan-2019-2023-paakayttaja)
        tilaaja (kemin-alueurakan-2019-2023-urakan-tilaajan-urakanvalvoja)
        kohde (merge default-paikkauskohde
                     {:urakka-id @kemin-alueurakan-2019-2023-id
                      :nimi "Tilamuutosten testikohde"
                      :paikkauskohteen-tila "ehdotettu"})
        ;; Urakoitsija luo kohteen
        kohde-id (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-paikkauskohde-urakalle
                                 urakoitsija
                                 kohde)
        kohde (merge kohde kohde-id)]
    ;; Urakoitsija yrittää merkitä kohteen tilatuksi
    (is (thrown? Exception (paivita-paikkaukkohteen-tila kohde "tilattu" urakoitsija))
        "Poikkeusta ei heitetty! Urakoitsija pystyi merkkaamaan paikkauskohteen tilatuksi.")


    ;; Urakoitsija yrittää merkitä kohteen hylätyksi
    (is (thrown? Exception (paivita-paikkaukkohteen-tila kohde "hylatty" urakoitsija))
        "Poikkeusta ei heitetty! Urakoitsija pystyi merkkaamaan paikkauskohteen hylätyksi.")

    ;; Tilaaja pystyy merkitsemään kohteen tilatuksi
    (is (= "tilattu" (:paikkauskohteen-tila (paivita-paikkaukkohteen-tila kohde "tilattu" tilaaja))))

    ;; Tilaaja yrittää merkitä kohteen hylätyksi tilatusta
    (is (thrown? Exception (paivita-paikkaukkohteen-tila kohde "hylatty" tilaaja))
        "Poikkeusta ei heitetty! Tilaaj pystyi merkkaamaan paikkauskohteen hylätyksi tilatusta.")

    ;; Tilaaja pystyy perumaan kohteen tilauksen
    (is (= "ehdotettu" (:paikkauskohteen-tila (paivita-paikkaukkohteen-tila kohde "ehdotettu" tilaaja))))

    ;; Tilaaja pystyy merkitsemään kohteen hylätyksi
    (is (= "hylatty" (:paikkauskohteen-tila (paivita-paikkaukkohteen-tila kohde "hylatty" tilaaja))))

    ;; Tilaaja yrittää merkitä kohteen hylätystä tilatuksi
    (is (thrown? Exception (paivita-paikkaukkohteen-tila kohde "tilattu" tilaaja))
        "Poikkeusta ei heitetty! Tilaaj pystyi merkkaamaan paikkauskohteen tilatuksi hylätystä.")

    ;; Tilaaja pystyy perumaan kohteen hylkäyksen
    (is (= "ehdotettu" (:paikkauskohteen-tila (paivita-paikkaukkohteen-tila kohde "ehdotettu" tilaaja))))))

(deftest tallenna-puutteelliset-paikkauskohteet-excelista-kantaan
  (let [workbook (xls/load-workbook-from-file "test/resurssit/excel/Paikkausehdotukset.xlsx")
        paikkauskohteet (p-excel/erottele-paikkauskohteet workbook)
        _ (println "paikkauskohteet" (pr-str paikkauskohteet))
        ]
    ;; Tallennetaan kantaan - mikä ei onnistu koska tieto on puutteellista
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-paikkauskohde-urakalle
                                           +kayttaja-jvh+
                                           (first paikkauskohteet)))
        "Poikkeusta ei heitetty! Excelin paikkauskohde olikin validi!")))

;; TODO: Keksi miten kutsu-palvelua saa toimimaan ja käytä sitä tämän sijaan.
(defn vastaanota-excel [urakka-id kayttaja tiedoston-nimi]
  (paikkauskohteet/vastaanota-excel (:db jarjestelma) {:params {"urakka-id" (str urakka-id)
                                                                "file" {:tempfile (io/file tiedoston-nimi)}}
                                                       :kayttaja kayttaja}))

(deftest tallenna-validit-paikkauskohteet-excelista-kantaan
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        filtteroi-testin-kohteet (fn [pkt] (filter #(= "Happy day excel testi" (:lisatiedot %)) pkt))
        paikkauskohteet-ennen-tallennusta (kutsu-palvelua (:http-palvelin jarjestelma)
                                                          :paikkauskohteet-urakalle
                                                          +kayttaja-jvh+
                                                          {:urakka-id urakka-id})
        vastaus (vastaanota-excel urakka-id +kayttaja-jvh+ "test/resurssit/excel/Paikkausehdotukset_valid.xlsx")
        paikkauskohteet-tallennuksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                             :paikkauskohteet-urakalle
                                                             +kayttaja-jvh+
                                                             {:urakka-id urakka-id})]
    ;; Tarkistetaan, että saatiin neljä
    (is (= 200) (:status vastaus))
    (is (= 0 (count (filtteroi-testin-kohteet paikkauskohteet-ennen-tallennusta))) "Testin kohteet löytyvät jo kannasta, testikantaa ei ole siivottu.")
    (is (= 4 (count (filtteroi-testin-kohteet paikkauskohteet-tallennuksen-jalkeen))) "Kannasta ei löydy testin kohteita.")))

(deftest yrita-tallentaa-virheellinen-paikkaukohde-excelista-kantaan
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        filtteroi-testin-kohteet (fn [pkt] (filter #(= "Virheellinen, työmenetelmä puuttuu" (:lisatiedot %)) pkt))
        vastaus (vastaanota-excel urakka-id +kayttaja-jvh+ "test/resurssit/excel/Paikkausehdotukset_yksi_virhe.xlsx")
        vastaus-body (json/read-str (:body vastaus)) ;; Vastaus tulee jsonina joten parsitaan se ymmärrettävämpään muotoon
        paikkauskohteet-tallennuksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                             :paikkauskohteet-urakalle
                                                             +kayttaja-jvh+
                                                             {:urakka-id urakka-id})
        ]
    (is (= "Paikkauskohteen työmenetelmässä virhe" (first (get (first (get vastaus-body "virheet")) "virhe"))))
    (is (= 0 (count (filtteroi-testin-kohteet paikkauskohteet-tallennuksen-jalkeen))))))