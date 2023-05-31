(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [clojure.set :as set]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [cheshire.core :as cheshire]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet :as paikkauskohteet]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-excel :as p-excel]
            [harja.kyselyt.tieverkko :as tieverkko-kyselyt]
            [harja.domain.paikkaus :as paikkaus]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.tyokalut.paikkaus-test :refer :all]
            [harja.pvm :as pvm]
            [dk.ative.docjure.spreadsheet :as xls]
            [clojure.java.io :as io]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.konversio :as konv]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :paikkauskohteet (component/using
                                           (paikkauskohteet/->Paikkauskohteet false) ;; Asetetaan kehitysmoodi falseksi
                                           [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn default-paikkauskohde [ulkoinen-id]
  {:ulkoinen-id ulkoinen-id
   :nimi "testinimi"
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
   :tyomenetelma 8})

(deftest paikkauskohteet-urakalle-testi
  (let [_ (hae-kemin-paallystysurakan-2019-2023-id)
        urakka-id @kemin-alueurakan-2019-2023-id
        paikkauskohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :paikkauskohteet-urakalle
                                        +kayttaja-jvh+
                                        {:urakka-id urakka-id})]
    (is (> (count paikkauskohteet) 0))))

;; Testataan käyttäjää, jolla ei ole oikeutta mihinkään
(deftest paikkauskohteet-urakalle-seppo-testi
  (let [_ (hae-kemin-paallystysurakan-2019-2023-id)
        urakka-id @kemin-alueurakan-2019-2023-id]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :paikkauskohteet-urakalle
                                           +kayttaja-seppo+
                                           {:urakka-id urakka-id}))
        "Poikkeusta ei heitetty! Sepolla olikin oikeus hakea paikkauskohteet.")))

;; Haetaan paikkauskohteet käyttäjälle, jolla ei ole oikeutta nähdä hintatietoja (urakan laadunvalvoja)
(deftest paikkauskohteet-ilman-kustannustietoja-testi
  (let [_ (hae-kemin-paallystysurakan-2019-2023-id)
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
  (let [_ (hae-kemin-paallystysurakan-2019-2023-id)
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
                  (assoc :tie 22)
                  (assoc :alkupvm (pvm/->pvm "01.01.2020"))
                  (assoc :loppupvm (pvm/->pvm "01.02.2020"))
                  #_(assoc :paikkauskohteen-tila "valmis"))
        muokattu-kohde (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-paikkauskohde-urakalle
                                       +kayttaja-jvh+
                                       kohde)]
    (is (= (:id kohde) (:id muokattu-kohde)))))

(deftest luo-uusi-paikkauskohde-testi
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        kohde (merge {:urakka-id urakka-id}
                (default-paikkauskohde (rand-int 999999)))

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
        kohde (dissoc (merge {:urakka-id urakka-id} (default-paikkauskohde (rand-int 999999)))
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
        kohde (merge (default-paikkauskohde (rand-int 999999))
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
        kohde (merge (default-paikkauskohde (rand-int 999999))
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

(defn- paivita-paikkauskohteen-tila [kohde uusi-tila kayttaja]
  (let [kohde (kutsu-palvelua (:http-palvelin jarjestelma)
                              :tallenna-paikkauskohde-urakalle
                              kayttaja
                              (merge kohde {:paikkauskohteen-tila uusi-tila}))]
    kohde))

(deftest paikkauskohde-tilamuutokset-testi
  (let [urakoitsija (kemin-alueurakan-2019-2023-paakayttaja)
        tilaaja (lapin-paallystyskohteiden-tilaaja)
        kohde (merge (default-paikkauskohde (rand-int 999999))
                     {:urakka-id @kemin-alueurakan-2019-2023-id
                      :nimi "Tilamuutosten testikohde: ehdotettu"
                      :paikkauskohteen-tila "ehdotettu"})
        ;; Urakoitsija luo kohteen, joka on ehdotettu tilassa
        ehdotettu (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-paikkauskohde-urakalle
                                  urakoitsija
                                  kohde)
        ;; Urakoitsija luo kohteen, joka on ehdotettu tilassa
        ehdotettu2 (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :tallenna-paikkauskohde-urakalle
                                   urakoitsija
                                   (assoc (default-paikkauskohde (rand-int 999999))
                                     :urakka-id @kemin-alueurakan-2019-2023-id
                                     :paikkauskohteen-tila "ehdotettu"
                                     :nimi "Tilamuutosten testikohde: ehdotettu2"))
        ;; Urakoitsija luo kohteen, joka on tilattu tilassa
        tilattu (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-paikkauskohde-urakalle
                                urakoitsija
                                (assoc (default-paikkauskohde (rand-int 999999))
                                  :urakka-id @kemin-alueurakan-2019-2023-id
                                  :paikkauskohteen-tila "tilattu"
                                  :nimi "Tilamuutosten testikohde: tilattu"))
        ;; Urakoitsija luo kohteen, joka on hylatty
        hylatty (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-paikkauskohde-urakalle
                                urakoitsija
                                (assoc (default-paikkauskohde (rand-int 999999))
                                  :urakka-id @kemin-alueurakan-2019-2023-id
                                  :paikkauskohteen-tila "hylatty"
                                  :nimi "Tilamuutosten testikohde: hylatty"))
        ;; Urakoitsija luo kohteen, joka on valmis
        valmis (kutsu-palvelua (:http-palvelin jarjestelma)
                               :tallenna-paikkauskohde-urakalle
                               urakoitsija
                               (assoc (default-paikkauskohde (rand-int 999999))
                                 :urakka-id @kemin-alueurakan-2019-2023-id
                                 :paikkauskohteen-tila "valmis"
                                 :nimi "Tilamuutosten testikohde: valmis"))]

    ;; Negatiivinen tarkistus tilauksesta
    ;; Urakoitsija yrittää merkitä kohteen tilatuksi - ehdotettu -> tilattu
    (is (thrown? Exception (paivita-paikkauskohteen-tila ehdotettu "tilattu" urakoitsija))
        "Poikkeusta ei heitetty! Urakoitsija pystyi merkkaamaan paikkauskohteen tilatuksi.")
    ;; Positiivinen tarkistus tilauksesta
    ;; Tilaaja pystyy merkitsemään kohteen tilatuksi
    (is (= "tilattu" (:paikkauskohteen-tila (paivita-paikkauskohteen-tila ehdotettu "tilattu" tilaaja))))

    ;; Urakoitsija yrittää merkitä kohteen hylätyksi - ehdotettu -> hylatty
    (is (thrown? Exception (paivita-paikkauskohteen-tila ehdotettu "hylatty" urakoitsija))
        "Poikkeusta ei heitetty! Urakoitsija pystyi merkkaamaan paikkauskohteen hylätyksi.")

    ;; Tilaaja yrittää merkitä kohteen hylätyksi tilatusta - tilattu -> hylatty
    (is (thrown? Exception (paivita-paikkauskohteen-tila tilattu "hylatty" tilaaja))
        "Poikkeusta ei heitetty! Tilaaja pystyi merkkaamaan paikkauskohteen hylätyksi tilatusta.")

    ;; Tilaaja pystyy perumaan kohteen tilauksen - tilattu -> ehdotettu, jos paikkauskohteella ei ole toteumia
    (is (= "ehdotettu" (:paikkauskohteen-tila (paivita-paikkauskohteen-tila tilattu "ehdotettu" tilaaja))))

    ;; Tilaaja pystyy merkitsemään kohteen hylätyksi - ehdotettu -> hylatty
    (is (= "hylatty" (:paikkauskohteen-tila (paivita-paikkauskohteen-tila ehdotettu2 "hylatty" tilaaja))))

    ;; Tilaaja yrittää merkitä kohteen hylätystä tilatuksi
    (is (thrown? Exception (paivita-paikkauskohteen-tila hylatty "tilattu" tilaaja))
        "Poikkeusta ei heitetty! Tilaaja pystyi merkkaamaan paikkauskohteen tilatuksi hylätystä.")

    ;; Tilaaja pystyy perumaan kohteen hylkäyksen - hylatty -> ehdotettu
    (is (= "ehdotettu" (:paikkauskohteen-tila (paivita-paikkauskohteen-tila hylatty "ehdotettu" tilaaja))))

    ;; Tilaaja pystyy muuttamaan valmiin kohteen tilatuksi - valmis - tilattu
    (is (= "tilattu" (:paikkauskohteen-tila (paivita-paikkauskohteen-tila valmis "tilattu" tilaaja))))
    ))

(deftest tallenna-puutteelliset-paikkauskohteet-excelista-kantaan
  (let [workbook (xls/load-workbook-from-file "test/resurssit/excel/Paikkausehdotukset.xlsx")
        paikkauskohteet (p-excel/erottele-paikkauskohteet workbook)]
    ;; Tallennetaan kantaan - mikä ei onnistu koska tieto on puutteellista
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-paikkauskohde-urakalle
                                           +kayttaja-jvh+
                                           (first paikkauskohteet)))
        "Poikkeusta ei heitetty! Excelin paikkauskohde olikin validi!")))

;; TODO: Keksi miten kutsu-palvelua saa toimimaan ja käytä sitä tämän sijaan.
(defn vastaanota-excel [urakka-id kayttaja tiedoston-nimi]
  (paikkauskohteet/vastaanota-excel (:db jarjestelma) nil nil {:params {"urakka-id" (str urakka-id)
                                                                        "file" {:tempfile (io/file tiedoston-nimi)}}
                                                               :kayttaja kayttaja}
                                    false)) ;; Kehitysmoodi falseksi

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

(deftest laske-tien-osien-pituudet
  (let [keksityt-osat '({:osa 1 :pituus 100}
                        {:osa 2 :pituus 100}
                        {:osa 3 :pituus 100}
                        {:osa 4 :pituus 100})
        keksitty-kohde1 {:tie 1 :aosa 1 :aet 0 :losa 2 :let 10}
        laskettu1 (tieverkko-kyselyt/laske-tien-osien-pituudet keksityt-osat keksitty-kohde1)
        keksitty-kohde11 {:tie 1 :aosa 1 :aet 0 :losa 5 :let 100}
        laskettu11 (tieverkko-kyselyt/laske-tien-osien-pituudet keksityt-osat keksitty-kohde11)

        keksitty-kohde2 {:tie 1 :aosa 1 :aet 0 :losa 3 :let 50}
        laskettu2 (tieverkko-kyselyt/laske-tien-osien-pituudet keksityt-osat keksitty-kohde2)

        keksitty-kohde3 {:tie 1 :aosa 2 :aet 20 :losa 2 :let 80}
        laskettu3 (tieverkko-kyselyt/laske-tien-osien-pituudet keksityt-osat keksitty-kohde3)

        keksitty-kohde4 {:tie 1 :aosa 2 :aet 10 :losa 1 :let 0}
        laskettu4 (tieverkko-kyselyt/laske-tien-osien-pituudet keksityt-osat keksitty-kohde4)
        keksitty-kohde41 {:tie 1 :aosa 4 :aet 100 :losa 1 :let 0}
        laskettu41 (tieverkko-kyselyt/laske-tien-osien-pituudet keksityt-osat keksitty-kohde41)
        ]
    ;; Perus case, otetaan osasta 1 loput ja osan 2 alku
    (is (= 110 (:pituus laskettu1)))
    (is (= 400 (:pituus laskettu11)))

    ;; Perus case 2, otetaan osasta 1 loput ja osan 2 alku
    (is (= 250 (:pituus laskettu2)))

    ;; Vaikeampi tapaus - otetaan osasta 2 osaan 2, niin että vain väliin jäävä pätkä lasketaan
    ;; Esim jos osa 2 on 100m pitkä ja otetaan kohdasta 20 kohtaan 80 tulee 60m
    (is (= 60 (:pituus laskettu3)))

    ;; Erikoistapaus, jossa tierekisterin osat merkataan eri järjestyksessä. Eli loppuosa on pienempi, kuin alkuosa
    ;; Nyt tuloksena pitäisi olla nil
    (is (= 110 (:pituus laskettu4)))
    (is (= 400 (:pituus laskettu41)))))

;; Testataan käsin lisätyn paikkauksen toimintaa
(defn testipaikkaus [paikkauskohde-id urakka-id kayttaja-id]
  {:alkuaika #inst"2021-04-21T10:47:24.183975000-00:00"
   :loppuaika #inst"2021-04-21T11:47:24.183975000-00:00"
   :tyomenetelma 5
   :paikkauskohde-id paikkauskohde-id
   :urakka-id urakka-id
   :tie 20
   :aosa 1
   :aet 1
   :losa 1
   :let 100
   :ajorata 1})

;;
;;Happycase
(deftest tallenna-paikkaussoiro-kasin-test
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        kohde (merge {:urakka-id urakka-id}
                (default-paikkauskohde (rand-int 999999)))
        tyomenetelmat @paikkauskohde-tyomenetelmat
        kayttaja-id (:id +kayttaja-jvh+)
        paikkauskohde (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tallenna-paikkauskohde-urakalle
                                      +kayttaja-jvh+
                                      (assoc kohde :paikkauskohteen-tila "tilattu"))
        paikkaus (testipaikkaus (:id paikkauskohde) urakka-id (:id +kayttaja-jvh+))
        tallennettu-paikkaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-kasinsyotetty-paikkaus
                                             +kayttaja-jvh+
                                             paikkaus)
        vertailtava-paikkaus (dissoc tallennettu-paikkaus ::paikkaus/id ::paikkaus/alkuaika ::paikkaus/sijainti ::paikkaus/loppuaika)
        odotettu-paikkaus {:harja.domain.paikkaus/tyomenetelma 5,
                           :harja.domain.paikkaus/ulkoinen-id 0,
                           :harja.domain.paikkaus/paikkauskohde-id (:id paikkauskohde)
                           :harja.domain.muokkaustiedot/luoja-id kayttaja-id,
                           :harja.domain.paikkaus/lahde "harja-ui",
                           :harja.domain.paikkaus/urakka-id 37,
                           :harja.domain.paikkaus/tierekisteriosoite #:harja.domain.tierekisteri{:tie 20, :aosa 1, :aet 1, :losa 1, :let 100 :ajorata 1},
                           :harja.domain.paikkaus/massatyyppi ""}]
    (is (= vertailtava-paikkaus odotettu-paikkaus))
    (is (= 5 (::paikkaus/tyomenetelma tallennettu-paikkaus) ;; Konetiivistetty reikävaluasfalttipaikkaus (REPA)
           #_(hae-tyomenetelman-arvo :nimi :id (::paikkaus/tyomenetelma tallennettu-paikkaus))))
    (is (= (:id paikkauskohde) (::paikkaus/paikkauskohde-id tallennettu-paikkaus)))
    ))


;; Tallennetaan käsin lisättävä levittimellä tehty paikkaus
(defn testipaikkauslevittimella [paikkauskohde-id urakka-id kayttaja-id]
  {:alkuaika #inst"2021-06-21T10:47:24.183975000-00:00"
   :loppuaika #inst"2021-06-21T11:47:24.183975000-00:00"
   :tyomenetelma 1
   :paikkauskohde-id paikkauskohde-id
   :urakka-id urakka-id
   :tie 81
   :aosa 1
   :aet 1
   :losa 1
   :let 100
   :ajorata 1
   :kaista 2
   :massamaara 3.3
   :pinta-ala 3
   :massamenekki 3.33
   :kuulamylly "3"
   :raekoko 5,
   :massatyyppi "SMA, Kivimastiksiasfaltti"
   })

;;Happycase
(deftest tallenna-levittimella-tehty-paikkaussoiro-kasin-test
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        kohde (merge {:urakka-id urakka-id}
                (default-paikkauskohde (rand-int 999999)))
        tyomenetelmat @paikkauskohde-tyomenetelmat

        paikkauskohde (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tallenna-paikkauskohde-urakalle
                                      +kayttaja-jvh+
                                      (assoc kohde :paikkauskohteen-tila "tilattu"))
        paikkaus (testipaikkauslevittimella (:id paikkauskohde) urakka-id (:id +kayttaja-jvh+))
        tallennettu-paikkaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-kasinsyotetty-paikkaus
                                             +kayttaja-jvh+
                                             paikkaus)]
    (is (= 1 (::paikkaus/tyomenetelma tallennettu-paikkaus))) ;; AB-paikkaus levittimellä
    (is (= (:id paikkauskohde) (::paikkaus/paikkauskohde-id tallennettu-paikkaus)))))

(deftest tallenna-levittimella-tehty-paikkaussoiro-kasin-epaonnistuu-test

  (let [urakka-id @kemin-alueurakan-2019-2023-id
        kohde (merge {:urakka-id urakka-id}
                (default-paikkauskohde (rand-int 999999)))
        paikkauskohde (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tallenna-paikkauskohde-urakalle
                                      +kayttaja-jvh+
                                      (assoc kohde :paikkauskohteen-tila "tilattu"))
        paikkaus (testipaikkauslevittimella (:id paikkauskohde) urakka-id (:id +kayttaja-jvh+))
        ;; Muutetaan alkuosa liian suureksi.
        paikkaus (assoc paikkaus :aosa 99999999999999
                                 :aet 100
                                 :losa 1
                                 :let 99)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-kasinsyotetty-paikkaus
                                           +kayttaja-jvh+
                                           paikkaus))
        "Poikkeusta ei heitetty, vaikka tierekisteriosoite ei ole validi")))


(defn hae-paikkaukset [urakka-id paikkauskohde-id]
  (let [paikkaukset (q-map (str "SELECT * FROM paikkaus
                              WHERE poistettu = false
                                AND \"urakka-id\" = " urakka-id "
                                AND \"paikkauskohde-id\" = " paikkauskohde-id " ;"))]
    paikkaukset))

;;Happycase
(deftest poista-kasin-lisatty-paikkaus-test
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        kohde (merge {:urakka-id urakka-id}
                (default-paikkauskohde (rand-int 999999)))
        paikkauskohde (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tallenna-paikkauskohde-urakalle
                                      +kayttaja-jvh+
                                      (assoc kohde :paikkauskohteen-tila "tilattu"))
        alkup-paikkausmaara (count (hae-paikkaukset urakka-id (:id paikkauskohde)))
        paikkaus (testipaikkaus (:id paikkauskohde) urakka-id (:id +kayttaja-jvh+))
        tallennettu-paikkaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-kasinsyotetty-paikkaus
                                             +kayttaja-jvh+
                                             paikkaus)
        tallennettu-paikkaus (set/rename-keys tallennettu-paikkaus paikkaus/speqcl-avaimet->paikkaus)
        tallennettu-paikkausmaara (count (hae-paikkaukset urakka-id (:id paikkauskohde)))
        ;; Poistetaan paikkaus
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :poista-kasinsyotetty-paikkaus
                          +kayttaja-jvh+
                          tallennettu-paikkaus)
        poistettu-paikkausmaara (count (hae-paikkaukset urakka-id (:id paikkauskohde)))]
    (is (= alkup-paikkausmaara poistettu-paikkausmaara))
    (is (= (inc alkup-paikkausmaara) tallennettu-paikkausmaara))))

(deftest lisaa-urem-paikkaus-excelista
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        kohde (merge {:urakka-id urakka-id}
                (default-paikkauskohde (rand-int 999999)))
        paikkauskohde (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-paikkauskohde-urakalle
                        +kayttaja-jvh+
                        (assoc kohde :paikkauskohteen-tila "tilattu"))
        alkup-paikkausmaara (count (hae-paikkaukset urakka-id (:id paikkauskohde)))
        lue-excelista (kutsu-excel-vienti-palvelua (:http-palvelin jarjestelma)
                        :lue-urapaikkaukset-excelista
                        +kayttaja-jvh+
                        {:urakka-id urakka-id
                         :paikkauskohde-id (:id paikkauskohde)}
                        "test/resurssit/excel/urem_tuonti.xlsx")
        paikkaukset-jalkeen (into []
                                  (comp
                                    (map #(update % :tierekisteriosoite konv/lue-tr-osoite)))
                                  (hae-paikkaukset urakka-id (:id paikkauskohde)))
        eka-rivi (first (filter #(= 50 (get-in % [:tierekisteriosoite :loppuetaisyys]))

                                paikkaukset-jalkeen))
        toka-rivi (first (filter #(= 100 (get-in % [:tierekisteriosoite :loppuetaisyys]))

                                paikkaukset-jalkeen))
        urem-kohteen-kokonaismassamaara (ffirst (q (str "SELECT urem_kok_massamaara FROM paikkauskohde WHERE id = " (:id paikkauskohde) ";")))]

    (is (= (:status lue-excelista) 200))
    (is (= urem-kohteen-kokonaismassamaara 1.5M) "Kohteen kokonaismassamäärä")
    (is (= (:tierekisteriosoite eka-rivi) {:alkuetaisyys 0
                                           :alkuosa 1
                                           :loppuetaisyys 50
                                           :loppuosa 1
                                           :numero 22}) "Tierekisteriosoite oikein")
    (is (= (:tierekisteriosoite toka-rivi) {:alkuetaisyys 0
                                           :alkuosa 3
                                           :loppuetaisyys 100
                                           :loppuosa 3
                                           :numero 22}) "Tierekisteriosoite oikein")
    (is (= alkup-paikkausmaara 0) "Paikkauskohteella ei pitäisi olla paikkauksia ennen excel-tuontia")
    (is (= (count paikkaukset-jalkeen) 2) "Excel-tuonnista pitäisi tulla kaksi paikkausta")

    (is (= (:massatyyppi (first paikkaukset-jalkeen)) "AB, Asfalttibetoni"))
    (is (= (:massatyyppi (second paikkaukset-jalkeen)) "AB, Asfalttibetoni"))
    (is (= (:massamenekki (first paikkaukset-jalkeen)) 15.38M))))

(deftest lisaa-urem-paikkaus-excelista-epaonnistuu
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        kohde (merge {:urakka-id urakka-id}
                (default-paikkauskohde (rand-int 999999)))
        paikkauskohde (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-paikkauskohde-urakalle
                        +kayttaja-jvh+
                        (assoc kohde :paikkauskohteen-tila "tilattu"))
        paikkaukset-ennen (hae-paikkaukset urakka-id (:id paikkauskohde))
        lue-excelista (kutsu-excel-vienti-palvelua (:http-palvelin jarjestelma)
                        :lue-urapaikkaukset-excelista
                        +kayttaja-jvh+
                        {:urakka-id urakka-id
                         :paikkauskohde-id (:id paikkauskohde)}
                        "test/resurssit/excel/urem_tuonti_fail.xlsx")
        virheet1 (get-in (cheshire/decode (:body lue-excelista)) ["virheet" "paikkausten-validointivirheet"])

        lue-roskaa-excelista (kutsu-excel-vienti-palvelua (:http-palvelin jarjestelma)
                               :lue-urapaikkaukset-excelista
                               +kayttaja-jvh+
                               {:urakka-id urakka-id
                                :paikkauskohde-id (:id paikkauskohde)}
                               "test/resurssit/excel/odottamaton-excel.xlsx")

        virheet2 (get-in (cheshire/decode (:body lue-roskaa-excelista)) ["virheet" "excel-luku-virhe"])
        paikkaukset-jalkeen (hae-paikkaukset urakka-id (:id paikkauskohde))]
    (is (= (:status lue-excelista) 400))
    (is (= (count paikkaukset-ennen) 0) "Paikkauskohteella ei pitäisi olla paikkauksia ennen excel-tuontia")
    (is (= (get virheet1 "5") ["Massamäärä puuttuu tai on virheellinen"]))
    (is (= virheet2 "Excelin otsikot eivät täsmää pohjaan"))
    (is (= (count paikkaukset-jalkeen) 0) "Excel-tuonnista ei pitäisi tulla paikkausta")))

