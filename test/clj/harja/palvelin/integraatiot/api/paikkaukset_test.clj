(ns harja.palvelin.integraatiot.api.paikkaukset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [harja.domain.paikkaus :as paikkaus]
            [harja.palvelin.integraatiot.api.paikkaukset :as api-paikkaukset]
            [harja.domain.tierekisteri :as tierekisteri]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-paikkaukset (component/using
                       (api-paikkaukset/->Paikkaukset)
                       [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki])))

(use-fixtures :once (compose-fixtures tietokanta-fixture
                                      jarjestelma-fixture))

(deftest kirjaa-paikkaus
  (let [db (luo-testitietokanta)
        urakka (hae-oulun-alueurakan-2014-2019-id)
        paikkaustunniste 3453455
        kohdetunniste 1231234
        json (->
               (slurp "test/resurssit/api/paikkauksen-kirjaus.json")
               (.replace "<PAIKKAUSTUNNISTE>" (str paikkaustunniste))
               (.replace "<KOHDETUNNISTE>" (str kohdetunniste)))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/paikkaus"] kayttaja portti json)
        odotettu-paikkaus {::paikkaus/tyomenetelma "massapintaus"
                           ::paikkaus/materiaalit [{::paikkaus/esiintyma "testi"
                                                    ::paikkaus/kuulamylly-arvo "testi"
                                                    ::paikkaus/muotoarvo "testi"
                                                    ::paikkaus/lisa-aineet "lisäaineet"
                                                    ::paikkaus/pitoisuus 1.2M
                                                    ::paikkaus/sideainetyyppi "20/30"}]
                           ::paikkaus/raekoko 1
                           ::paikkaus/ulkoinen-id 3453455
                           ::paikkaus/leveys 10M
                           ::paikkaus/urakka-id 4
                           ::paikkaus/tierekisteriosoite {::tierekisteri/aet 1
                                                          ::tierekisteri/let 16
                                                          ::tierekisteri/tie 20
                                                          ::tierekisteri/aosa 1
                                                          ::tierekisteri/losa 5}
                           ::paikkaus/massatyyppi "asfalttibetoni"
                           ::paikkaus/tienkohdat [{::paikkaus/ajourat [2 3]
                                                   ::paikkaus/ajorata 1
                                                   ::paikkaus/keskisaumat [1 1]
                                                   ::paikkaus/ajouravalit [5 7]
                                                   ::paikkaus/reunat [1]}]
                           ::paikkaus/kuulamylly "AN5"
                           ::paikkaus/paikkauskohde {::paikkaus/ulkoinen-id 1231234
                                                     ::paikkaus/nimi "Testipaikkauskohde"}

                           ::paikkaus/massamenekki 12}
        odotettu-kohde {::paikkaus/nimi "Testipaikkauskohde"
                        ::paikkaus/ulkoinen-id 1231234}]
    (is (= 200 (:status vastaus)) "Tietueen lisäys onnistui")
    (is (.contains (:body vastaus) "Paikkaukset kirjattu onnistuneesti"))
    (is (= odotettu-paikkaus (dissoc (first (paikkaus-q/hae-paikkaukset db {::paikkaus/ulkoinen-id paikkaustunniste}))
                                     ::paikkaus/id
                                     ::paikkaus/loppuaika
                                     ::paikkaus/alkuaika
                                     ::paikkaus/paikkauskohde-id)))
    (is (= odotettu-kohde (dissoc
                            (first (paikkaus-q/hae-paikkauskohteet db {::paikkaus/ulkoinen-id kohdetunniste}))
                            ::paikkaus/id)))))

(deftest kirjaa-paikkauskustannus
  (let [db (luo-testitietokanta)
        urakka (hae-oulun-alueurakan-2014-2019-id)
        toteumatunniste 234531
        kohdetunniste 466645
        json (->
               (slurp "test/resurssit/api/paikkaustoteuman-kirjaus.json")
               (.replace "<TOTEUMATUNNISTE>" (str toteumatunniste))
               (.replace "<KOHDETUNNISTE>" (str kohdetunniste)))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/paikkaus/kustannus"] kayttaja portti json)
        odotetut-paikkaustoteumat [{::paikkaus/selite "asfaltti"
                                    ::paikkaus/tyyppi "kokonaishintainen"
                                    ::paikkaus/ulkoinen-id 234531
                                    ::paikkaus/urakka-id 4}
                                   {::paikkaus/selite "Liikennejärjestelyt"
                                    ::paikkaus/tyyppi "yksikkohintainen"
                                    ::paikkaus/ulkoinen-id 234531
                                    ::paikkaus/urakka-id 4}]
        odotettu-kohde {::paikkaus/nimi "Testipaikkauskohde"
                        ::paikkaus/ulkoinen-id 466645}]

    (is (= 200 (:status vastaus)) "Tietueen lisäys onnistui")
    (is (.contains (:body vastaus) "Paikkauskustannukset kirjattu onnistuneesti"))
    (is (= odotetut-paikkaustoteumat (mapv
                                       #(dissoc % ::paikkaus/id ::paikkaus/paikkauskohde-id ::paikkaus/kirjattu)
                                       (paikkaus-q/hae-paikkaustoteumat db {::paikkaus/ulkoinen-id toteumatunniste}))))
    (is (= odotettu-kohde (dissoc
                            (first (paikkaus-q/hae-paikkauskohteet db {::paikkaus/ulkoinen-id kohdetunniste}))
                            ::paikkaus/id
                            ::paikkaus/kirjattu)))))

