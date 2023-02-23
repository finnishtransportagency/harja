(ns harja.palvelin.integraatiot.api.raportit-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.raportit :as api-raportit]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.konversio :as konv]))

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
    (let [urakka (first (q-map (str "SELECT id, urakkanro
                                       FROM urakka
                                      WHERE nimi = 'Oulun alueurakka 2014-2019'")))
          urakka-id (:id urakka)
          alueurakkanumero (konv/konvertoi->int (:urakkanro urakka))
          alkupvm "2014-10-01"
          loppupvm "2015-09-30"
          vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" urakka-id "/raportit/materiaali/" alkupvm "/" loppupvm)]
                    kayttaja portti)

          dekoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 200 (:status vastaus)))
      (is (= "Materiaaliraportti" (get-in dekoodattu-body [:raportti :nimi])))
      (is (= alueurakkanumero (get-in dekoodattu-body [:raportti :alueurakkanumero])))
      (is (= alkupvm (get-in dekoodattu-body [:raportti :aikavali :alkupvm])))
      (is (= loppupvm (get-in dekoodattu-body [:raportti :aikavali :loppupvm])))
      (is (=
            (sort-by :materiaali [{:materiaali "Natriumformiaatti" :maara {:yksikko "t" :maara 2000}}
                                  {:materiaali "Talvisuola, rakeinen NaCl" :maara {:yksikko "t" :maara 200}}
                                  {:materiaali "Talvisuolaliuos NaCl" :maara {:yksikko "t" :maara 1800}}
                                  {:materiaali "Hiekoitushiekka" :maara {:yksikko "t" :maara 0}}
                                  {:materiaali "Kuumapäällyste" :maara {:yksikko "t" :maara 1000}}
                                  {:materiaali "Massasaumaus" :maara {:yksikko "t" :maara 1000}}])
            (sort-by :materiaali (get-in dekoodattu-body [:raportti :materiaaliraportti]))))))

  (testing "Annettu aikaväli on liian suuri"
    (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
          alkupvm "2014-01-01"
          loppupvm "2019-12-31"
          vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" urakka-id "/raportit/materiaali/" alkupvm "/" loppupvm)]
                    kayttaja portti)
          dekoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 400 (:status vastaus)))
      (is (= [{:virhe {:koodi "virheellinen-aikavali"
                       :viesti "Annettu aikaväli on liian suuri. Suurin sallittu aikaväli on 1 vuosi."}}]
            (:virheet dekoodattu-body)))))

  (testing "Parametrit 'alkupvm' ja 'loppupvm' ovat väärinpäin"
    (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
          alkupvm "2019-12-31"
          loppupvm "2014-01-01"
          vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" urakka-id "/raportit/materiaali/" alkupvm "/" loppupvm)]
                    kayttaja portti)
          dekoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 400 (:status vastaus)))
      (is (= [{:virhe {:koodi "virheellinen-aikavali"
                       :viesti "'loppupvm' on ennen 'alkupvm'"}}]
            (:virheet dekoodattu-body)))))

  (testing "Tuntematon urakka"
    (let [urakka-id 3184391
          alkupvm "2014-01-10"
          loppupvm "2015-09-30"
          vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" urakka-id "/raportit/materiaali/" alkupvm "/" loppupvm)]
                    kayttaja portti)
          dekoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 400 (:status vastaus)))
      (is (= [{:virhe {:koodi "tuntematon-urakka", :viesti "Urakkaa id:llä 3184391 ei löydy."}}]
            (:virheet dekoodattu-body)))))

  (testing "Ei oikeuksia urakkaan"
    (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
          alkupvm "2019-01-01"
          loppupvm "2019-12-31"
          vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" urakka-id "/raportit/materiaali/" alkupvm "/" loppupvm)]
                    kayttaja portti)
          dekoodattu-body (cheshire/decode (:body vastaus) true)]
      (is (= 400 (:status vastaus)))
      (is (= [{:virhe {:koodi "kayttajalla-puutteelliset-oikeudet", :viesti "Käyttäjällä: yit-rakennus ei ole oikeuksia urakkaan: 5"}}]
            (:virheet dekoodattu-body))))))
