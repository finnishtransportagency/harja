(ns harja.palvelin.integraatiot.api.urakat-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.urakat :as api-urakat]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.kyselyt.konversio :as konversio]
            [clojure.string :as str]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
                                           :api-urakat
                                           (component/using
                                             (api-urakat/->Urakat)
                                             [:http-palvelin :db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn- lisaa-paallystyspalvelusopimus
  "Lisää päällystyspalvelusopimuksen annetulla urakkanumerolla = sopimusnumero. Mikäli haluat sopimuksen liittyvän
  johonkin urakkaan, tarkasta että testidatassa on voimassaoleva urakka, jonka urakkanumero vastaa sopimusnumeroa."
  [urakkanro]
  ;; Tienpätkä Kouvolasta (x 485685.087 y 6752597.811)
  (let [alue "MULTILINESTRING((485543.562 6752323.253,485571.523 6752328.833,485627.479 6752343.476,485715.597 6752369.102,485731.672 6752374.482),(485731.672 6752374.482,485769.855 6752387.273),(485769.855 6752387.273,485771.03 6752387.667,485831.954 6752406.756,485917.359 6752435.972),(485917.359 6752435.972,485950.666 6752447.809,486004.268 6752465.852,486030.754 6752475.426,486043.002 6752480.124,486056.439 6752484.783,486069.967 6752489.906,486077.903 6752493.541,486083.3 6752498.211),(485539.884 6752333.016,485588.519 6752344.522,485679.251 6752368.578,485725.925 6752383.875),(485725.925 6752383.875,485745.144 6752389.759,485765.017 6752395.512),(485765.017 6752395.512,485802.407 6752408.063,485862.548 6752427.674,485914.451 6752444.148),(485914.451 6752444.148,485957.726 6752457.963,485988.962 6752469.166,486034.6 6752484.679,486057.349 6752492.785,486072.253 6752497.23,486083.3 6752498.211))"]
    (u (str
         "INSERT INTO paallystyspalvelusopimus (alue, paallystyspalvelusopimusnro, paivitetty)
          VALUES (ST_GeomFromText('" alue "') :: GEOMETRY, '" urakkanro "', current_timestamp);"))))

(defn- poista-paallytyspalvelusopimus [urakkanro]
  (u (str "DELETE FROM paallystyspalvelusopimus WHERE paallystyspalvelusopimusnro = '" urakkanro "';")))

(deftest hae-jarjestelmakayttajan-urakat
  (let [_ (anna-lukuoikeus "yit-rakennus")
        vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "yit-rakennus" portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (ffirst (q "SELECT count(*) FROM urakka WHERE urakoitsija=(SELECT id FROM organisaatio WHERE nimi='YIT Rakennus Oy')")) (count (:urakat encoodattu-body))) "YIT:lle löytyy oikea määrä urakoita"))

  (let [_ (anna-lukuoikeus "tuntematon-jarjestelma")
        vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "tuntematon-jarjestelma" portti)]
    (is (= 403 (:status vastaus))))

  (let [_ (anna-lukuoikeus "carement")
        vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "carement" portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= 1 (count (:urakat encoodattu-body))))
    (is (= "Oulun alueurakka 2014-2019" (get-in (first (:urakat encoodattu-body)) [:urakka :tiedot :nimi]))))

  (let [_ (anna-lukuoikeus "livi")
        vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "livi" portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        urakoita-kannassa (ffirst (q "select count(id) from urakka where urakoitsija is not null and hallintayksikko is not null;"))]
    (is (= 200 (:status vastaus)))
    (is (= urakoita-kannassa (count (:urakat encoodattu-body))))))

(deftest hae-jarjestelmakayttajan-urakat-tyypeittain
  (let [urakkatyyppi "paallystys"
        _ (anna-lukuoikeus "livi")
        vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "livi" {"urakkatyyppi" urakkatyyppi} portti)
        urakat (:urakat (cheshire/decode (:body vastaus) true))]
    (is (= 200 (:status vastaus)))
    (is (every? #(= urakkatyyppi (get-in % [:urakka :tiedot :tyyppi])) urakat))))

(deftest hae-urakka-sijainnilla-ja-tyypilla
  (testing "Urakkatyyppi: hoito"
    (let [urakkatyyppi "hoito"
          _ (anna-lukuoikeus "yit-rakennus")
          vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/sijainnilla"] "yit-rakennus"
                    {"urakkatyyppi" urakkatyyppi
                     ;; Oulun lähiseutu (EPSG:3067)
                     "x" 427232.596 "y" 7211474.342} portti)
          enkoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 200 (:status vastaus)))
      (is (= 2 (count (map #(get-in % [:urakka :tiedot :nimi]) (:urakat enkoodattu-body)))))
      (is (every?
            (fn [nimi]
              ;; Testataan löytyykö resultsetistä oikeat urakan nimet, mutta ei oteta MHU:ssa huomioon vuosilukuja,
              ;; jotka voivat vaihtua testidataa päivittäessä.
              (some #(clojure.string/includes? nimi %) #{"Oulun MHU" "Aktiivinen Oulu Testi"}))
            (map #(get-in % [:urakka :tiedot :nimi]) (:urakat enkoodattu-body))))))

  (testing "Urakkatyyppi: paallystys (sopimustyyppi = palvelusopimus)"
    ;; Lisätään väliaikainen päällystyspalvelusopimus Porvoon päällystysurakalle (testisopimuksen geometria on Kouvolassa)
    (lisaa-paallystyspalvelusopimus "por1")

    (let [urakkatyyppi "paallystys"
          _ (anna-lukuoikeus (:kayttajanimi +kayttaja-paakayttaja-skanska+))
          vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/sijainnilla"] (:kayttajanimi +kayttaja-paakayttaja-skanska+)
                    {"urakkatyyppi" urakkatyyppi
                     ;; Kouvolan seutu (EPSG:3067)
                     "x" 485685.087 "y" 6752597.811}
                    portti)
          enkoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 200 (:status vastaus)))
      (is (= 1 (count (map #(get-in % [:urakka :tiedot :nimi]) (:urakat enkoodattu-body)))))
      (is (= "Porvoon päällystysurakka" (get-in (first (:urakat enkoodattu-body)) [:urakka :tiedot :nimi]))))

    ;; Poistetaan väliaikainen päällystyspalvelusopimus
    (poista-paallytyspalvelusopimus "por1"))


  (comment
    ;; Testi otettu pois käytöstä, kunnes päällystysurakoiden sijaintihaku kokonaisurakoiden osalta selviää.
    ;; Varsinainen haku on toteutettu urakat.sql 'hae-urakka-sijainnilla' kyselyssä.
    ;; Sieltä on nyt kommentoitu pois kokonaisurakka-sopimukseen kuuluvien päällystysurakoiden haku sijainnin perusteella
    ;; ja TODO-kommentti aiheeseen liittyen lisätty.
    ;; Ominaisuus on pois käytöstä sen takia, että t-loik ilmoitusten urakoiden haussa (hae-lahin-urakka-id-sijainnilla)
    ;; oletetaan osumia tulevan vain 'palvelusopimus' sopimustyyppisiin päällystysurakoihin.
    ;; Ilmoitusten urakkahaku antaa väärän tuloksen mikäli tulee osuma 'kokonaisurakka' päällystysurakkaan.
    ;; Ilmoitusten suhteen ollaan ilmeisesti kiinnostuneita vain palvelusopimuksen piirissä olevista urakoista.
    (testing "Urakkatyyppi: paallystys (sopimustyyppi = kokonaisurakka)"
             (let [urakkatyyppi "paallystys"
                   _ (anna-lukuoikeus (:kayttajanimi +kayttaja-paakayttaja-skanska+))
                   vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/sijainnilla"] (:kayttajanimi +kayttaja-paakayttaja-skanska+)
                             {"urakkatyyppi" urakkatyyppi
                              ;; Oulun lähiseutu (EPSG:3067)
                              "x" 427232.596 "y" 7211474.342} portti)
                   enkoodattu-body (cheshire/decode (:body vastaus) true)]
               (is (= 200 (:status vastaus)))
               (is (= 1 (count (map #(get-in % [:urakka :tiedot :nimi]) (:urakat enkoodattu-body)))))
               (is (= "Muhoksen päällystysurakka" (get-in (first (:urakat enkoodattu-body)) [:urakka :tiedot :nimi])))))))

(deftest hae-urakka-pelkalla-sijainnilla
  (testing "Sijainti (epsg:3067): 427232.596,7211474.342"
    ;; TODO: Pitäisi keksiä testikäyttäjä, jolla olisi oikeuksia vähän useampaan erilaiseen urakkatyyppiin.
    ;;       Siten saisi tässä testattua myös palautuuko samaan pisteeseen osuvat erilaiset urakkatyypit hausta.
    (let [_ (anna-lukuoikeus (:kayttajanimi +kayttaja-yit_uuvh+))
          vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/sijainnilla"] (:kayttajanimi +kayttaja-yit_uuvh+)
                    {;; Oulun lähiseutu (EPSG:3067)
                     "x" 427232.596 "y" 7211474.342} portti)
          enkoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 200 (:status vastaus)))
      (is (= 2 (count (map #(get-in % [:urakka :tiedot :nimi]) (:urakat enkoodattu-body)))))))

  (testing "Käyttäjällä ei oikeuksia urakoihin"
    (let [_ (anna-kirjoitusoikeus "livi")
          vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/sijainnilla"] "livi"
                    {;; Oulun lähiseutu (EPSG:3067)
                     "x" 427232.596 "y" 7211474.342} portti)
          enkoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 200 (:status vastaus)))
      ;; Livi käyttäjällä ei ole oikeuksia yhteenkään urakkaan, joka osuu hakuun.
      ;; Pitäisi palautua kolmen sijasta nolla urakkaa.
      (is (= 0 (count (map #(get-in % [:urakka :tiedot :nimi]) (:urakat enkoodattu-body))))))))


(deftest hae-urakka-idlla
         (let [_ (anna-lukuoikeus "yit-rakennus")
               vastaus (api-tyokalut/get-kutsu ["/api/urakat/1"] "yit-rakennus" portti)
               encoodattu-body (cheshire/decode (:body vastaus) true)
               tunnukset (mapv #(get-in % [:tehtava :id]) (get-in encoodattu-body [:urakka :tehtavat :yksikkohintaiset]))
               apitunnus 987654]
         (is (= 200 (:status vastaus)))
         (is (some #(= % apitunnus) tunnukset) "Tehtävien id on toimenpidekoodi-taulun apitunnus.")))


;; teiden-hoito urakkatyyppi palautetaan API:ssa hoito-urakkatyyppinä
(deftest varmista-urakkatyyppien-yhteensopivuus
  (let [json "{
              \"urakat\": [
                  {
                    \"urakka\": {
                      \"tiedot\": {
                          \"id\": 123456789,
                          \"nimi\": \"Oulun alueurakka\",
                          \"urakoitsija\": {
                          \"ytunnus\": \"123456-8\",
                          \"nimi\": \"Asfaltia\"
                        },
                        \"vaylamuoto\": \"tie\",
                        \"tyyppi\": \"[URAKKATYYPPI]\",
                        \"alkupvm\": \"2016-01-30T12:00:00+02:00\",
                        \"loppupvm\": \"2016-01-30T12:00:00+02:00\"
                      }
                    }
                  }
                ]
              }"
        urakkatyypit (konversio/pgarray->vector (ffirst (q "SELECT enum_range(NULL :: URAKKATYYPPI);")))]
    (doseq [urakkatyyppi urakkatyypit]
      (is (nil? (json-skeemat/urakoiden-haku-vastaus (.replace json "[URAKKATYYPPI]" (if (= "teiden-hoito" urakkatyyppi)
                                                                                       "hoito"
                                                                                       urakkatyyppi))))
          (format "JSON-skeema ei salli urakkatyyppiä: %s" urakkatyyppi)))))
