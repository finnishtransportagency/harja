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
            [harja.domain.tierekisteri :as tr-domain]
            [harja.domain.paikkaus :as paikkaus]
            [harja.tyokalut.paikkaus-test :refer :all]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.palvelin.integraatiot.api.paikkaukset :as api-paikkaukset]
            [harja.palvelin.palvelut.yllapitokohteet.paikkaukset :as palvelu-paikkaukset]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yllapitokohteet-yleiset]
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
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/paikkaus"] kayttaja portti json)
        odotettu-leveys 10M
        tr-osoite {::tierekisteri/aet 1
                   ::tierekisteri/let 16
                   ::tierekisteri/tie 20
                   ::tierekisteri/aosa 1
                   ::tierekisteri/losa 5}
        tr-osoite-tr-alkuisena (tr-domain/tr-alkuiseksi tr-osoite)
        osien-pituudet-tielle (yllapitokohteet-yleiset/laske-osien-pituudet db [tr-osoite-tr-alkuisena])
        paikkauksen-pituus (tr-domain/laske-tien-pituus (osien-pituudet-tielle 20) tr-osoite-tr-alkuisena) ;; 15953 m
        odotettu-pinta-ala (* paikkauksen-pituus odotettu-leveys) ;; 159530 m2
        odotettu-massamaara 1914.360M ;; laskettu kertomalla massamenekki t/m2 pinta-alan m2 kanssa (12 / 1000) * 159530
        odotettu-paikkaus {::paikkaus/tyomenetelma (hae-tyomenetelman-arvo :id :lyhenne "UREM" tyomenetelmat)
                           ::paikkaus/raekoko 1
                           ::paikkaus/ulkoinen-id 3453455
                           ::paikkaus/leveys odotettu-leveys
                           ::paikkaus/urakka-id 4
                           ::paikkaus/tierekisteriosoite tr-osoite
                           ::paikkaus/pinta-ala odotettu-pinta-ala
                           ::paikkaus/massamaara odotettu-massamaara
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
        _ (anna-kirjoitusoikeus kayttaja)
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
        _ (anna-kirjoitusoikeus kayttaja)
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
        _ (anna-kirjoitusoikeus kayttaja)
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

