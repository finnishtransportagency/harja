(ns harja.palvelin.integraatiot.api.paikkaukset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [harja.testi :refer :all]
            [specql.op :as op]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.paikkaus :as paikkaus]
            [harja.tyokalut.paikkaus-test :refer :all]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.palvelin.integraatiot.api.paikkaukset :as api-paikkaukset]
            [harja.palvelin.palvelut.yllapitokohteet.paikkaukset :as palvelu-paikkaukset]
            [harja.domain.tierekisteri :as tierekisteri]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all]
            [harja.palvelin.integraatiot.yha.yha-paikkauskomponentti :as yha-paikkauskomponentti]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :yha-paikkauskomponentti (component/using
                               (yha-paikkauskomponentti/->YhaPaikkaukset {:url +yha-url+})
                               [:db :integraatioloki])
    :api-paikkaukset (component/using
                       (api-paikkaukset/->Paikkaukset)
                       [:http-palvelin :db :integraatioloki :yha-paikkauskomponentti])))

(use-fixtures :each (compose-fixtures tietokanta-fixture
                                      jarjestelma-fixture))

(deftest kirjaa-paikkaus
  (let [db (luo-testitietokanta)
        urakka (hae-oulun-alueurakan-2014-2019-id)
        tyomenetelmat (hae-paikkauskohde-tyomenetelmat)
        paikkaustunniste 3453455
        kohdetunniste 1231234
        json (->
               (slurp "test/resurssit/api/paikkauksen-kirjaus.json")
               (.replace "<PAIKKAUSTUNNISTE>" (str paikkaustunniste))
               (.replace "<KOHDETUNNISTE>" (str kohdetunniste)))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/paikkaus"] kayttaja portti json)
        odotettu-paikkaus {::paikkaus/tyomenetelma (hae-tyomenetelman-arvo :id :lyhenne "UREM" tyomenetelmat)
                           ::paikkaus/raekoko 1
                           ::paikkaus/ulkoinen-id 3453455
                           ::paikkaus/leveys 10M
                           ::paikkaus/urakka-id 4
                           ::paikkaus/tierekisteriosoite {::tierekisteri/aet 1
                                                          ::tierekisteri/let 16
                                                          ::tierekisteri/tie 20
                                                          ::tierekisteri/aosa 1
                                                          ::tierekisteri/losa 5}
                           ::paikkaus/massatyyppi "AB, Asfalttibetoni"
                           ::paikkaus/kuulamylly "AN5"
                           ::paikkaus/massamenekki 12M
                           ::paikkaus/lahde "harja-api"}
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
                                                          ::paikkaus/paikkauskohteen-tila "tilattu"
                                                          ::paikkaus/nimi "Testipaikkauskohde"}}
        odotettu-kohde #:harja.domain.paikkaus{:nimi "Testipaikkauskohde"
                                               :paikkauskohteen-tila "tilattu"
                                               :ulkoinen-id 1231234
                                               :urakka-id 4}
        paikkaus (first (paikkaus-q/hae-paikkaukset db {::paikkaus/ulkoinen-id paikkaustunniste}))
        materiaali (first (paikkaus-q/hae-paikkaukset-materiaalit db {::paikkaus/ulkoinen-id paikkaustunniste}))
        tienkohta (first (paikkaus-q/hae-paikkaukset-tienkohta db {::paikkaus/ulkoinen-id paikkaustunniste}))
        paikkauskohde (first (paikkaus-q/hae-paikkaukset-paikkauskohde db {::paikkaus/ulkoinen-id paikkaustunniste}))]
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
                                                                         (dissoc paikkauskohde ::paikkaus/id ::muokkaustiedot/luotu))))))
    (is (= odotettu-kohde (dissoc
                            (first (paikkaus-q/hae-paikkauskohteet db {::paikkaus/ulkoinen-id kohdetunniste}))
                            ::paikkaus/id ::muokkaustiedot/luotu)))))

(deftest kirjaa-paikkaustietoja
  (let [db (luo-testitietokanta)
        urakka (hae-oulun-alueurakan-2014-2019-id)
        toteumatunniste 234531
        kohdetunniste 466645
        json (->
               (slurp "test/resurssit/api/paikkaustoteuman-kirjaus.json")
               (.replace "<TOTEUMATUNNISTE>" (str toteumatunniste))
               (.replace "<KOHDETUNNISTE>" (str kohdetunniste)))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/paikkaus/kustannus"] kayttaja portti json)
        poisto-json (slurp "test/resurssit/api/paikkaustietojen-poisto.json")
        poistettu-ennen (:poistettu (first (q-map "SELECT * FROM paikkauskohde WHERE lisatiedot = 'Oulun testipaikkauskohde';")))
        poisto-vastaus (api-tyokalut/delete-kutsu ["/api/urakat/" urakka "/paikkaus"] kayttaja portti poisto-json)
        poistettu-jalkeen (:poistettu (first (q-map "SELECT * FROM paikkauskohde WHERE lisatiedot = 'Oulun testipaikkauskohde';")))
        odotetut-paikkaustoteumat [{:harja.domain.paikkaus/selite "Lähtömaksu",
                                    :harja.domain.paikkaus/urakka-id 4,
                                    :harja.domain.paikkaus/hinta 450.3M,
                                    :harja.domain.paikkaus/ulkoinen-id 234531,
                                    :harja.domain.paikkaus/tyyppi "kokonaishintainen"}
                                   {:harja.domain.paikkaus/selite "Liikennejärjestelyt",
                                    :harja.domain.paikkaus/urakka-id 4,
                                    :harja.domain.paikkaus/hinta 2000M,
                                    :harja.domain.paikkaus/ulkoinen-id 234531,
                                    :harja.domain.paikkaus/tyyppi "kokonaishintainen"}]
        odotettu-kohde #:harja.domain.paikkaus{:nimi "Testipaikkauskohde"
                                               :ulkoinen-id 466645
                                               :paikkauskohteen-tila "tilattu"
                                               :urakka-id 4}]

    (is (false? poistettu-ennen) "Ei ole alussa poistettu")
    (is (true? poistettu-jalkeen) "Poisto onnistui")
    (is (= 200 (:status vastaus)) "Tietueen lisäys onnistui")
    (is (.contains (:body vastaus) "Paikkauskustannukset kirjattu onnistuneesti"))
    (is (= odotetut-paikkaustoteumat (mapv
                                       #(dissoc % ::paikkaus/id ::paikkaus/paikkauskohde-id ::paikkaus/kirjattu)
                                       (paikkaus-q/hae-paikkaustoteumat db {::paikkaus/ulkoinen-id toteumatunniste}))))
    (is (= odotettu-kohde (dissoc
                            (first (paikkaus-q/hae-paikkauskohteet db {::paikkaus/ulkoinen-id kohdetunniste}))
                            ::paikkaus/id
                            ::paikkaus/kirjattu
                            ::muokkaustiedot/luotu
                            ::muokkaustiedot/muokattu)))
    ;; palauttaa 500, koska yha-paikkauskomponenttia ei ole mockattu. No biggie.
    (is (= 500 (:status poisto-vastaus)) "Poistokutsu epäonnistui")))

;; Paikkaukset yleisimmin kirjataan tieoisoitteelle tyyliin tie: 1, aosa 1, losa: 2. aet:1 let: 1, josta voi tulla
;; pituudeksi esim 1000m
;; Mutta kirjauksen voi tehdä myös toisen päin, eli tie: 1, aosa 2, losa: 1. aet:1 let: 1, josta voi tulla esim 1000m,
;; jos pituus osataan laskea oikein.
(deftest kirjaa-paikkaus-pienenevalla-tieosoitteella

  (let [db (luo-testitietokanta)
        urakka (hae-oulun-alueurakan-2014-2019-id)
        aikavali [#inst "2022-01-05T00:00:00.000-00:00"
                  #inst "2022-01-05T20:59:59.000-00:00"]
        paikkaustunniste 200
        kohdetunniste 1231234
        ;; Lähetettävän paikkaustoteuman tieosoitteen pituus on 12 605 m
        oletettu-pituus 12605
        json (->
               (slurp "test/resurssit/api/paikkausten-kirjaus-tieosoite-toisin-pain.json")
               (.replace "<PAIKKAUSTUNNISTE>" (str paikkaustunniste))
               (.replace "<KOHDETUNNISTE>" (str kohdetunniste))
               (.replace "<AOSA>" (str 5))
               (.replace "<LOSA>" (str 3)))
        json-vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/paikkaus"] kayttaja portti json)
        paikkaukset (palvelu-paikkaukset/hae-urakan-paikkaukset db +kayttaja-jvh+
                      {:urakka-id urakka
                       :aikavali aikavali
                       :tyomenetelmat #{"Kaikki"}
                       ;:tr nil
                       :nayta false})
        luotu-paikkaus (first (keep (fn [p]
                                      (when (= kohdetunniste (::paikkaus/ulkoinen-id p))
                                        p))
                                paikkaukset))]
    (is (= oletettu-pituus (:suirun-pituus (first (::paikkaus/paikkaukset luotu-paikkaus)))))
    (is (= 200 (:status json-vastaus)))))

(deftest kirjaa-paikkaus-pienenevalla-tieosoitteella-varmistus

  (let [db (luo-testitietokanta)
        urakka (hae-oulun-alueurakan-2014-2019-id)
        aikavali [#inst "2022-01-05T00:00:00.000-00:00"
                  #inst "2022-01-05T20:59:59.000-00:00"]
        paikkaustunniste 200
        kohdetunniste 1231234
        ;; Lähetettävän paikkaustoteuman tieosoitteen pituus on 500 m
        oletettu-pituus 500
        json (->
               (slurp "test/resurssit/api/paikkausten-kirjaus-tieosoite-toisin-pain.json")
               (.replace "<PAIKKAUSTUNNISTE>" (str paikkaustunniste))
               (.replace "<KOHDETUNNISTE>" (str kohdetunniste))
               (.replace "<AOSA>" (str 3))
               (.replace "<LOSA>" (str 3)))
        json-vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/paikkaus"] kayttaja portti json)
        paikkaukset (palvelu-paikkaukset/hae-urakan-paikkaukset db +kayttaja-jvh+
                      {:urakka-id urakka
                       :aikavali aikavali
                       :tyomenetelmat #{"Kaikki"}
                       ;:tr nil
                       :nayta false})
        luotu-paikkaus (first (keep (fn [p]
                                      (when (= kohdetunniste (::paikkaus/ulkoinen-id p))
                                        p))
                                paikkaukset))]
    (is (= oletettu-pituus (:suirun-pituus (first (::paikkaus/paikkaukset luotu-paikkaus)))))
    (is (= 200 (:status json-vastaus)))))

(defn- trosoite-obj->map
  "Konvertoi paikkaustoteuman tierekisterosoitteen tietokannan objsta clojuremapiksi konvertterin avulla."
  [p]
  (konv/pgobject->map
    (:tierekisteriosoite p)
    :tie :string
    :aosa :string
    :aet :string
    :losa :string
    :let :string
    :ajorata :string))

(deftest kirjaa-paikkaus-ja-muokkaa-onnistuneesti
  (let [urakka (hae-oulun-alueurakan-2014-2019-id)
        paikkaustunniste 200
        paikkaustunniste2 201
        kohdetunniste 1231234
        json1 (->
                (slurp "test/resurssit/api/paikkausten-kirjaus-tieosoite-toisin-pain.json")
                (.replace "<PAIKKAUSTUNNISTE>" (str paikkaustunniste))
                (.replace "<KOHDETUNNISTE>" (str kohdetunniste))
                (.replace "<AOSA>" (str 3))
                (.replace "<LOSA>" (str 4)))
        json2 (->
                (slurp "test/resurssit/api/paikkausten-kirjaus-tieosoite-toisin-pain.json")
                (.replace "<PAIKKAUSTUNNISTE>" (str paikkaustunniste2))
                (.replace "<KOHDETUNNISTE>" (str kohdetunniste))
                (.replace "<AOSA>" (str 3))
                (.replace "<LOSA>" (str 22))
                )
        json1-vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/paikkaus"] kayttaja portti json1)
        let-3000 (trosoite-obj->map (first (q-map "SELECT * FROM paikkaus WHERE \"ulkoinen-id\" = " paikkaustunniste ";")))
        json2-vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/paikkaus"] kayttaja portti json2)
        losa-22 (trosoite-obj->map (first (q-map "SELECT * FROM paikkaus WHERE \"ulkoinen-id\" = " paikkaustunniste2 ";")))]
    (is (= 200 (:status json1-vastaus)))
    (is (= 200 (:status json2-vastaus)))
    ;; Ensimmäisessä toteumassa loppuosa on 5000m
    (is (= "3000" (:let let-3000)))
    ;; Muokatussa toteumassa loppuosa on 6000m
    (is (= "22" (:losa losa-22)))))

;; TODO: Rakenna testiaineisto yit-käyttäjälle. Ja testaa poistoja.

;
;;; Paikkauskohteen poisto
;;;
;;; Toisen urakoitsijan kohdetta ei voi poistaa
;(is (= 1 (count (paikkaus-q/hae-paikkauskohteet db {::paikkaus/ulkoinen-id 666
;                                                           ::paikkaus/urakka-id 4
;                                                           ::muokkaustiedot/luoja-id 9
;                                                           ::muokkaustiedot/poistettu? true}))) "Paikkauskohteen poisto epäonnistui (1).")
;
;;;; Saman käyttäjän, samalla ulkoisella id:llä eri urakkaan tallentamaa kohdetta ei päivitetty
;(is (= 1 (count (paikkaus-q/hae-paikkauskohteet db {::paikkaus/ulkoinen-id 666
;                                                           ::paikkaus/urakka-id 21
;                                                           ::muokkaustiedot/luoja-id 9
;                                                           ::muokkaustiedot/poistettu? false}))) "Paikkauskohteen poisto epäonnistui (2).")
;;; Poistettavan kohteen kaikki paikkaukset merkittiin poistetuksi
;(is (= 0 (count (paikkaus-q/hae-paikkaukset db {::paikkaus/paikkauskohde-id 1
;                                                       ::paikkaus/urakka-id 4
;                                                       ::muokkaustiedot/luoja-id 9
;                                                       ::muokkaustiedot/poistettu? false}))) "Paikkausten poisto epäonnistui (1).")
;;; Poistettavan kohteen kaikki toteumat merkittiin poistetuksi
;(is (= 0 (count (paikkaus-q/hae-paikkaustoteumat db {::paikkaus/paikkauskohde-id 1
;                                                            ::paikkaus/urakka-id 4
;                                                            ::muokkaustiedot/luoja-id 9
;                                                            ::muokkaustiedot/poistettu? false}))) "Paikkauskustannusten poisto epäonnistui (1).")
;
;;; Paikkauksen poisto
;;;
;;; Poistettavat paikkaukset merkittiin poistetuksi
;(is (= 2 (count (paikkaus-q/hae-paikkaukset db {::paikkaus/ulkoinen-id (op/in #{221, 222})
;                                                       ::paikkaus/urakka-id 4
;                                                       ::muokkaustiedot/luoja-id 9
;                                                       ::muokkaustiedot/poistettu? true}))) "Paikkausten poisto epäonnistui (2).")
;;; Saman paikkauskohteen muut paikkaukset ovat vielä voimassa
;(is (= 2 (count (paikkaus-q/hae-paikkaukset db {::paikkaus/urakka-id 4
;                                                       ::paikkaus/paikkauskohde-id 4
;                                                       ::muokkaustiedot/luoja-id 9
;                                                       ::muokkaustiedot/poistettu? false}))) "Paikkausten poisto epäonnistui (3).")
;
;;; Paikkaustoteuman poisto
;;;
;;; Poistettavat paikkaustoteumat merkittiin poistetuksi
;(is (= 1 (count (paikkaus-q/hae-paikkaustoteumat db {::paikkaus/ulkoinen-id 133
;                                                            ::paikkaus/urakka-id 4
;                                                            ::muokkaustiedot/luoja-id 9
;                                                            ::muokkaustiedot/poistettu? true}))) "Paikkauskustannusten poisto epäonnistui (2).")
;;; Saman paikkauskohteen muut paikkaukset ovat vielä voimassa
;(is (= 1 (count (paikkaus-q/hae-paikkaustoteumat db {::paikkaus/urakka-id 4
;                                                            ::paikkaus/paikkauskohde-id 3
;                                                            ::muokkaustiedot/luoja-id 9
;                                                            ::muokkaustiedot/poistettu? false}))))) "Paikkauskustannusten poisto epäonnistui (3).")
;

