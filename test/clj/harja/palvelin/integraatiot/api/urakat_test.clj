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

(deftest hae-jarjestelmakayttajan-urakat
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "yit-rakennus" portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= (ffirst (q "SELECT count(*) FROM urakka WHERE urakoitsija=(SELECT id FROM organisaatio WHERE nimi='YIT Rakennus Oy')")) (count (:urakat encoodattu-body))) "YIT:lle löytyy oikea määrä urakoita"))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "tuntematon-jarjestelma" portti)]
    (is (= 403 (:status vastaus))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "carement" portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= 1 (count (:urakat encoodattu-body))))
    (is (= "Oulun alueurakka 2014-2019" (get-in (first (:urakat encoodattu-body)) [:urakka :tiedot :nimi]))))

  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "livi" portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)
        urakoita-kannassa (ffirst (q "select count(id) from urakka where urakoitsija is not null and hallintayksikko is not null;"))]
    (is (= 200 (:status vastaus)))
    (is (= urakoita-kannassa (count (:urakat encoodattu-body))))))

(deftest hae-jarjestelmakayttajan-urakat-tyypeittain
  (let [urakkatyyppi "paallystys"
        vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/"] "livi" {"urakkatyyppi" urakkatyyppi} portti)
        urakat (:urakat (cheshire/decode (:body vastaus) true))]
    (is (= 200 (:status vastaus)))
    (is (every? #(= urakkatyyppi (get-in % [:urakka :tiedot :tyyppi])) urakat))))

(deftest hae-urakka-sijainnilla-ja-tyypilla
  (testing "Urakkatyyppi: hoito"
    (let [urakkatyyppi "hoito"
          vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/sijainnilla"] "livi"
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

  (testing "Urakkatyyppi: paallystys"
    (let [urakkatyyppi "paallystys"
          vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/sijainnilla"] "livi"
                    {"urakkatyyppi" urakkatyyppi
                     ;; Oulun lähiseutu (EPSG:3067)
                     "x" 427232.596 "y" 7211474.342} portti)
          enkoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 200 (:status vastaus)))
      ;; TODO: Muhoksen päällystysurakka pitäisi saada tärppäämään hakuun
      (is (= 1 (count (map #(get-in % [:urakka :tiedot :nimi]) (:urakat enkoodattu-body)))))
      (is (= "Muhoksen päällystysurakka" (get-in (first (:urakat enkoodattu-body)) [:urakka :tiedot :nimi]))))))

(deftest hae-urakka-pelkalla-sijainnilla
  (testing "Sijainti (epsg:3067): 427232.596,7211474.342"
    (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/haku/sijainnilla"] "livi"
                    {;; Oulun lähiseutu (EPSG:3067)
                     "x" 427232.596 "y" 7211474.342} portti)
          enkoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 200 (:status vastaus)))
      ;; TODO: Ainakin päällystysurakat pitäisi saada vielä tärppäämään hakuun.
      ;;       Muut erikoisemmat urakkatyypit myös vielä testaamatta ja sopivat hakukoordinaatit & testiurakat testejä varten
      ;;       ovat vielä kysymysmerkkejä.
      #_(is (= :FOR-DEBUGGING-RESPONSE-DIFF enkoodattu-body))
      ;; TODO: Nyt tulee vain hoidon urakoita osumiin, samalla alueella pitäisi olla myös päällystysurakka Muhoksen päällystysurakka
      (is (= 3 (count (map #(get-in % [:urakka :tiedot :nimi]) (:urakat enkoodattu-body))))))))


(deftest hae-urakka-idlla
         (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/1"] "yit-rakennus" portti)
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
