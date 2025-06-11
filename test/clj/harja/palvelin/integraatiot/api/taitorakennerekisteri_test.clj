(ns harja.palvelin.integraatiot.api.taitorakennerekisteri-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.taitorakennerekisteri :as api-taitorakennerekisteri]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]))

(def kayttaja "taitorakenne-testeri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
    :api-taitorakennerekisteri
    (component/using
      (api-taitorakennerekisteri/->Taitorakennerekisteri)
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
      (is (not (nil? dekoodattu-body))))))

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
