(ns harja.palvelin.integraatiot.api.taitorakennerekisteri-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.taitorakennerekisteri :as api-taitorakennerekisteri]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.tyokalut.json-validointi :as json]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]))

(def kayttaja "taitorakenne-testeri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
    :api-taitorakennerekisteri
    (component/using
      (api-taitorakennerekisteri/->Taitorakennerekisteri "harja.testi" false)
      [:http-palvelin :db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest hae-siltatarkastukset-onnistuu
  (testing "Siltatarkastusten haku onnistuu oikeilla parametreilla"
    (let [alkuaika "2023-01-01T00:00:00+02:00"
          loppuaika "2023-12-31T23:59:59+02:00"
          vastaus (api-tyokalut/get-kutsu
                    [(str "/api/taitorakennerekisteri/siltatarkastukset/" alkuaika "/" loppuaika)]
                    kayttaja portti)
          dekoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 200 (:status vastaus)))
      (is (not (nil? dekoodattu-body)))
      (is (nil? (json/validoi
                  json-skeemat/+taitorakennerekisteri-siltatarkastukset-haku-vastaus+
                  (:body vastaus)))
        "Vastaus on JSON-skeeman mukainen"))))

(deftest hae-siltatarkastukset-vaarat-parametrit
  (testing "Virheelliset päivämäärät"
    (let [alkuaika "virheellinen-pvm"
          loppuaika "2023-12-31T23:59:59+02:00"
          vastaus (api-tyokalut/get-kutsu
                    [(str "/api/taitorakennerekisteri/siltatarkastukset/" alkuaika "/" loppuaika)]
                    kayttaja portti)]
      (is (= 400 (:status vastaus))))))

(deftest hae-siltatarkastukset-ei-oikeuksia
  (testing "Kutsu epäonnistuu ilman oikeuksia"
    (let [alkuaika "2023-01-01T00:00:00+02:00"
          loppuaika "2023-12-31T23:59:59+02:00"
          _ (poista-kayttajan-api-oikeudet kayttaja)
          vastaus (api-tyokalut/get-kutsu
                    [(str "/api/taitorakennerekisteri/siltatarkastukset/" alkuaika "/" loppuaika)]
                    kayttaja portti)]
      (is (= 403 (:status vastaus))))))

(deftest hae-siltatarkastukset-oikeuksien-hallinta
  (testing "Käyttäjälle annetaan taitorakenne oikeus"
    (let [alkuaika "2023-01-01T00:00:00+02:00"
          loppuaika "2023-12-31T23:59:59+02:00"
          kayttaja-ilman-oikeuksia "yit-rakennus"
          
          kutsu-epaonnistuu (api-tyokalut/get-kutsu
                              [(str "/api/taitorakennerekisteri/siltatarkastukset/" alkuaika "/" loppuaika)]
                              kayttaja-ilman-oikeuksia portti)
          _ (anna-taitorakenneoikeus kayttaja-ilman-oikeuksia)
          kutsu-onnistuu (api-tyokalut/get-kutsu
                           [(str "/api/taitorakennerekisteri/siltatarkastukset/" alkuaika "/" loppuaika)]
                           kayttaja-ilman-oikeuksia portti)]
      (is (= 403 (:status kutsu-epaonnistuu)))
      (is (= 200 (:status kutsu-onnistuu))))))

(deftest hae-sillan-siltatarkastukset-onnistuu
  (testing "Sillan siltatarkastuksen haku onnistuu oikeilla parametreilla"
    (let [silta_oid "9.2.246.578.1.15.40174"
          vastaus (api-tyokalut/get-kutsu
                    [(str "/api/taitorakennerekisteri/siltatarkastukset/" silta_oid)]
                    kayttaja portti)
          dekoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 200 (:status vastaus)))
      (is (not (nil? dekoodattu-body)))
      (is (nil? (json/validoi
                  json-skeemat/+taitorakennerekisteri-siltatarkastukset-haku-vastaus+
                  (:body vastaus)))
        "Vastaus on JSON-skeeman mukainen"))))

(deftest hae-sillan-siltatarkastukset-vaara-parametri
  (testing "Virheellinen silta_oid"
    (let [silta_oid ""
          vastaus (api-tyokalut/get-kutsu
                    [(str "/api/taitorakennerekisteri/siltatarkastukset/" silta_oid)]
                    kayttaja portti)]
      (is (= 403 (:status vastaus))))))

(deftest hae-sillan-siltatarkastukset-olematon-silta
  (testing "Haku olemattomalla silta-oid:lla palauttaa virheen"
    (let [silta_oid "9.9.999.999.9.99.99999" 
          vastaus (api-tyokalut/get-kutsu
                    [(str "/api/taitorakennerekisteri/siltatarkastukset/" silta_oid)]
                    kayttaja portti)
          dekoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 400 (:status vastaus)))
      (is (= "tuntematon-silta" (get-in (first (:virheet dekoodattu-body)) [:virhe :koodi]))))))

(deftest hae-sillan-siltatarkastukset-ei-oikeuksia
  (testing "Kutsu epäoonnistuu ilman oikeuksia"
    (let [silta_oid "9.2.246.578.1.15.40174"
          _ (poista-kayttajan-api-oikeudet kayttaja)
          vastaus (api-tyokalut/get-kutsu
                    [(str "/api/taitorakennerekisteri/siltatarkastukset/" silta_oid)]
                    kayttaja portti)]
      (is (= 403 (:status vastaus))))))

(deftest harja-url-rakentaminen-toimii
  (testing "Harja-URL muodostetaan oikein siltatarkastuksille"
    (let [alkuaika "2006-01-01T00:00:00+02:00"
          loppuaika "2008-12-31T23:59:59+02:00"
          vastaus (api-tyokalut/get-kutsu
                    [(str "/api/taitorakennerekisteri/siltatarkastukset/" alkuaika "/" loppuaika)]
                    kayttaja portti)
          dekoodattu-body (cheshire/decode (:body vastaus) true)
          ensimmainen-tarkastus (first (:siltatarkastukset dekoodattu-body))]
      (is (= 200 (:status vastaus)))
      (when ensimmainen-tarkastus
        (let [harja-url (get-in ensimmainen-tarkastus [:siltatarkastus :harja-url])
              urakka-id (get-in ensimmainen-tarkastus [:siltatarkastus :urakka :harja-id])
              hallintayksikko-id (get-in ensimmainen-tarkastus [:siltatarkastus :urakka :hallintayksikko])
              silta-id (get-in ensimmainen-tarkastus [:siltatarkastus :silta :harja-id])
              tarkastus-id (get-in ensimmainen-tarkastus [:siltatarkastus :harja-id])]
          (is (not (nil? harja-url)) "Harja-URL ei saa olla nil")
          (is (.contains harja-url "https://harja.testi") "URL sisältää base URL:n")
          (is (.contains harja-url "#urakat/laadunseuranta/siltatarkastukset") "URL sisältää oikean polun")
          (is (.contains harja-url (str "hy=" hallintayksikko-id)) "URL sisältää hallintayksikko-parametrin")
          (is (.contains harja-url (str "u=" urakka-id)) "URL sisältää urakka-parametrin")
          (is (.contains harja-url (str "sil=" silta-id)) "URL sisältää silta-parametrin")
          (is (.contains harja-url (str "st=" tarkastus-id)) "URL sisältää siltatarkastus-parametrin"))))))
