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

(use-fixtures :each (compose-fixtures tietokanta-fixture
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
                           ::paikkaus/kuulamylly "AN5"
                           ::paikkaus/massamenekki 12}
        odotettu-materiaali {::paikkaus/materiaalit [{::paikkaus/esiintyma "testi"
                                                     ::paikkaus/kuulamylly-arvo "testi"
                                                     ::paikkaus/muotoarvo "testi"
                                                     ::paikkaus/lisa-aineet "lisäaineet"
                                                     ::paikkaus/pitoisuus 1.2M
                                                     ::paikkaus/sideainetyyppi "20/30"}]}
        odotettu-tienkohta {::paikkaus/tienkohdat [{::paikkaus/ajourat [2 3]
                                                    ::paikkaus/ajorata 1
                                                    ::paikkaus/keskisaumat [1 1]
                                                    ::paikkaus/ajouravalit [5 7]
                                                    ::paikkaus/reunat [1]}]}
        odotettu-paikkauskohde {::paikkaus/paikkauskohde {::paikkaus/ulkoinen-id 1231234
                                                          ::paikkaus/nimi "Testipaikkauskohde"}}
        odotettu-kohde {::paikkaus/nimi "Testipaikkauskohde"
                        ::paikkaus/ulkoinen-id 1231234}
        paikkaus (first (paikkaus-q/hae-paikkaukset db {::paikkaus/ulkoinen-id paikkaustunniste}))
        materiaali (first (paikkaus-q/hae-paikkaukset-materiaalit db {::paikkaus/ulkoinen-id paikkaustunniste}))
        tienkohta (first (paikkaus-q/hae-paikkaukset-tienkohta db {::paikkaus/ulkoinen-id paikkaustunniste}))
        paikkauskohde (first (paikkaus-q/hae-paikkaukset-paikkauskohe db {::paikkaus/ulkoinen-id paikkaustunniste}))]
    (is (= 200 (:status vastaus)) "Tietueen lisäys onnistui")
    (is (.contains (:body vastaus) "Paikkaukset kirjattu onnistuneesti"))
    (is (= odotettu-paikkaus (dissoc paikkaus
                                     ::paikkaus/id
                                     ::paikkaus/sijainti
                                     ::paikkaus/loppuaika
                                     ::paikkaus/alkuaika
                                     ::paikkaus/paikkauskohde-id)))
    (is (= odotettu-materiaali (-> (select-keys materiaali [::paikkaus/materiaalit])
                                   (update ::paikkaus/materiaalit (fn [materiaalit]
                                                                    [(dissoc (first materiaalit) ::paikkaus/materiaali-id)])))))
    (is (= odotettu-tienkohta (-> (select-keys tienkohta [::paikkaus/tienkohdat])
                                   (update ::paikkaus/tienkohdat (fn [tienkohta]
                                                                    [(dissoc (first tienkohta) ::paikkaus/tienkohta-id)])))))
    (is (= odotettu-paikkauskohde (-> (select-keys paikkauskohde [::paikkaus/paikkauskohde])
                                   (update ::paikkaus/paikkauskohde (fn [paikkauskohde]
                                                                      (dissoc paikkauskohde ::paikkaus/id))))))
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
        odotetut-paikkaustoteumat [{:harja.domain.paikkaus/yksikkohinta 200M,
                                    :harja.domain.paikkaus/maara 12.5M,
                                    :harja.domain.paikkaus/selite "asfaltti",
                                    :harja.domain.paikkaus/tyyppi "yksikkohintainen",
                                    :harja.domain.paikkaus/ulkoinen-id 234531,
                                    :harja.domain.paikkaus/urakka-id 4,
                                    :harja.domain.paikkaus/yksikko "t"}
                                   {:harja.domain.paikkaus/yksikkohinta 20.4M,
                                    :harja.domain.paikkaus/maara 14.5M,
                                    :harja.domain.paikkaus/selite "bitumi",
                                    :harja.domain.paikkaus/tyyppi "yksikkohintainen",
                                    :harja.domain.paikkaus/ulkoinen-id 234531,
                                    :harja.domain.paikkaus/urakka-id 4,
                                    :harja.domain.paikkaus/yksikko "kg"}
                                   {:harja.domain.paikkaus/selite "Lähtömaksu",
                                    :harja.domain.paikkaus/urakka-id 4,
                                    :harja.domain.paikkaus/hinta 450.3M,
                                    :harja.domain.paikkaus/ulkoinen-id 234531,
                                    :harja.domain.paikkaus/tyyppi "kokonaishintainen"}
                                   {:harja.domain.paikkaus/selite "Liikennejärjestelyt",
                                    :harja.domain.paikkaus/urakka-id 4,
                                    :harja.domain.paikkaus/hinta 2000M,
                                    :harja.domain.paikkaus/ulkoinen-id 234531,
                                    :harja.domain.paikkaus/tyyppi "kokonaishintainen"}]
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

