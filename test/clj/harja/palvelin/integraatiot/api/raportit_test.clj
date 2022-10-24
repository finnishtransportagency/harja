(ns harja.palvelin.integraatiot.api.raportit-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.raportit :as api-raportit]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.kyselyt.konversio :as konversio]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
                                           :api-raportit
                                           (component/using
                                             (api-raportit/->Raportit)
                                             [:http-palvelin :db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest hae-urakan-materiaaliraportti

  (testing "Kysely OK"
    (let [urakka-id 4
          alkupvm "2019-01-01"
          loppupvm "2019-12-31"
          vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" urakka-id "/raportit/materiaali/" alkupvm "/" loppupvm)]
                    kayttaja portti)
          dekoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 200 (:status vastaus)))
      (is (= "Materiaaliraportti" (get-in dekoodattu-body [:raportti :nimi])))
      (is (= alkupvm (get-in dekoodattu-body [:raportti :aikavali :alkupvm])))
      (is (= alkupvm (get-in dekoodattu-body [:raportti :aikavali :loppupvm])))
      (is (seq (get-in dekoodattu-body [:raportti :materiaaliraportti])))))

  (testing "Annettu aikaväli on liian suuri"
    (let [urakka-id 4
          alkupvm "2014-01-01"
          loppupvm "2019-12-31"
          vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" urakka-id "/raportit/materiaali/" alkupvm "/" loppupvm)]
                    kayttaja portti)
          encoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 400 (:status vastaus)))
      (is (= [{:virhe {:koodi "virheellinen-aikavali", :viesti "Annettu aikaväli on liian suuri."}}]
            (:virheet encoodattu-body)))))

  (testing "Tuntematon urakka"
    (let [urakka-id 3184391
          alkupvm "2019-01-01"
          loppupvm "2019-12-31"
          vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" urakka-id "/raportit/materiaali/" alkupvm "/" loppupvm)]
                    kayttaja portti)
          encoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 400 (:status vastaus)))
      (is (=  [{:virhe {:koodi "tuntematon-urakka", :viesti "Urakkaa id:llä 3184391 ei löydy."}}]
            (:virheet encoodattu-body)))))

  (testing "Ei oikeuksia urakkaan"
    (let [urakka-id 5
          alkupvm "2019-01-01"
          loppupvm "2019-12-31"
          vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" urakka-id "/raportit/materiaali/" alkupvm "/" loppupvm)]
                    kayttaja portti)
          encoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 400 (:status vastaus)))
      (is (=  [{:virhe {:koodi "kayttajalla-puutteelliset-oikeudet", :viesti "Käyttäjällä: yit-rakennus ei ole oikeuksia urakkaan: 5"}}]
            (:virheet encoodattu-body))))))
