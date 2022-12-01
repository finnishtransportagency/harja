(ns harja.palvelin.integraatiot.yha.urakan-kohteen-lahetys-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.kyselyt.yha :as yha-kyselyt]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma :as kohteen-lahetyssanoma]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.pvm :as pvm]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [try+]]))

(def kayttaja "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :yha (component/using
           (yha/->Yha {:url +yha-url+})
           [:db :http-palvelin :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn tee-url []
  (str +yha-url+ "toteumatiedot"))

(defn hae-kohteen-lahetystiedot [kohde-id]
  (let [tila (first (q (format "SELECT lahetetty, lahetys_onnistunut FROM yllapitokohde WHERE id = %s" kohde-id)))]
    {:lahetetty (first tila)
     :lahetys_onnistunut (second tila)}))

(defn tyhjenna-kohteen-lahetystiedot [kohde-id]
  (u (format "UPDATE yllapitokohde SET lahetetty = NULL, lahetys_onnistunut = NULL WHERE id = %s" kohde-id)))

(deftest tarkista-yllapitokohteen-lahetys
  (let [kohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        urakka-yhaid (:yhaid (first (q-map (str "SELECT yhaid FROM yhatiedot WHERE urakka = " urakka-id ";"))))
        vuosi-nyt (pvm/vuosi (pvm/nyt))
        url (tee-url)
        onnistunut-kirjaus-vastaus "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<urakan-kohteiden-toteumatietojen-kirjausvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">\n</urakan-kohteiden-toteumatietojen-kirjausvastaus>"]
    (with-fake-http
      [{:url url :method :post}
       (fn [_ {:keys [url body] :as opts} _]
         (is (= url (:url opts)) "Kutsu tehdään oikeaan osoitteeseen")
         ;; Tarkistetaan, että lähtevässä XML:ssä on oikea data
         (let [luettu-xml (-> (xml/lue body))
               urakka (xml/luetun-xmln-tagien-sisalto
                        luettu-xml
                        :urakan-kohteiden-toteumatietojen-kirjaus :urakka)
               kohde (xml/luetun-xmln-tagien-sisalto
                       urakka
                       :kohteet :kohde)
               tr-osoite (xml/luetun-xmln-tagin-sisalto kohde :tierekisteriosoitevali)]
           (is (= (xml/luetun-xmln-tagin-sisalto urakka :yha-id) [(str urakka-yhaid)]))
           (is (= (xml/luetun-xmln-tagin-sisalto urakka :harja-id) [(str urakka-id)]))

           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdenumero) ["111"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdetyyppi) ["1"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdetyotyyppi) ["paallystys"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :nimi) ["Ouluntie"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :toiden-aloituspaivamaara) [(str vuosi-nyt "-05-19")]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :paallystyksen-valmistumispaivamaara) [(str vuosi-nyt "-05-21")]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohteen-valmistumispaivamaara) [(str vuosi-nyt "-05-24")]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :takuupaivamaara) ["2022-12-31"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :toteutunuthinta) ["5043.95"]))
           (is (= (xml/luetun-xmln-tagien-sisalto kohde :alustalle-tehdyt-toimet :alustalle-tehty-toimenpide :verkon-tarkoitus) ["1"]))

           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :tienumero) ["22"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :aosa) ["12"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :aet) ["4336"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :losa) ["12"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :let) ["9477"])))
         ;; Palautetaan vastaus
         onnistunut-kirjaus-vastaus)]

      (let [onnistui? (nil? (yha/laheta-kohteet (:yha jarjestelma) urakka-id [kohde-id]))
            lahetystiedot (hae-kohteen-lahetystiedot kohde-id)]
        (is (true? onnistui?))
        (is (not (nil? (:lahetetty lahetystiedot))) "Lähetysaika on merkitty")
        (is (true? (:lahetys_onnistunut lahetystiedot))) "Lähetys on merkitty onnistuneeksi")
      (tyhjenna-kohteen-lahetystiedot kohde-id))))

(defn assertoi-tr-osoite [alikohteen-xml odotettu-tr-osoite]
  (is (= (xml/luetun-xmln-tagien-sisalto alikohteen-xml :ajorata)
         [(:ajorata odotettu-tr-osoite)]))
  (is (= (xml/luetun-xmln-tagien-sisalto alikohteen-xml :kaista)
         [(:kaista odotettu-tr-osoite)]))
  (is (= (xml/luetun-xmln-tagien-sisalto alikohteen-xml :tienumero)
         [(:tienumero odotettu-tr-osoite)]))
  (is (= (xml/luetun-xmln-tagien-sisalto alikohteen-xml :aosa)
         [(:aosa odotettu-tr-osoite)]))
  (is (= (xml/luetun-xmln-tagien-sisalto alikohteen-xml :aet)
         [(:aet odotettu-tr-osoite)]))
  (is (= (xml/luetun-xmln-tagien-sisalto alikohteen-xml :losa)
         [(:losa odotettu-tr-osoite)]))
  (is (= (xml/luetun-xmln-tagien-sisalto alikohteen-xml :let)
         [(:let odotettu-tr-osoite)])))

(defn- alikohteen-tr-osoite [alikohteet positio]
  (xml/luetun-xmln-tagien-sisalto alikohteet {:tagi :alikohde :positio positio} :tierekisteriosoitevali))

(defn- alustan-tr-osoite [alustarivit positio]
  (xml/luetun-xmln-tagien-sisalto alustarivit {:tagi :alustalle-tehty-toimenpide :positio positio} :tierekisteriosoitevali))

(deftest tarkista-yllapitokohteen-lahetys-pot2
  (let [kohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        urakka-yhaid (:yhaid (first (q-map (str "SELECT yhaid FROM yhatiedot WHERE urakka = " urakka-id ";"))))
        ;; Kohteen vuosi ei muutu vuoden vaihtuessa, joten tehdään kova koodaatuna
        vuosi-nyt  2021 #_ (pvm/vuosi (pvm/nyt))
        url (tee-url)
        onnistunut-kirjaus-vastaus "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<urakan-kohteiden-toteumatietojen-kirjausvastaus xmlns=\"http://www.vayla.fi/xsd/yha\">\n</urakan-kohteiden-toteumatietojen-kirjausvastaus>"]
    (with-fake-http
      [{:url url :method :post}
       (fn [_ {:keys [url body] :as opts} _]
         (is (= url (:url opts)) "Kutsu tehdään oikeaan osoitteeseen")
         ;; Tarkistetaan, että lähtevässä XML:ssä on oikea data
         (let [luettu-xml (-> (xml/lue body))
               urakka (xml/luetun-xmln-tagien-sisalto
                        luettu-xml
                        :urakan-kohteiden-toteumatietojen-kirjaus :urakka)
               kohde (xml/luetun-xmln-tagien-sisalto
                       urakka
                       :kohteet :kohde)
               alustatoimeet (xml/luetun-xmln-tagien-sisalto kohde :alustalle-tehdyt-toimet)
               alikohteet (xml/luetun-xmln-tagien-sisalto kohde :alikohteet)
               tr-osoite (xml/luetun-xmln-tagin-sisalto kohde :tierekisteriosoitevali)]

           (is (= (xml/luetun-xmln-tagin-sisalto urakka :yha-id) [(str urakka-yhaid)]))
           (is (= (xml/luetun-xmln-tagin-sisalto urakka :harja-id) [(str urakka-id)]))

           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdenumero) ["116"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdetyyppi) ["1"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdetyotyyppi) ["paallystys"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :nimi) ["Tärkeä kohde mt20"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :toiden-aloituspaivamaara) [(str vuosi-nyt "-06-19")]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :paallystyksen-valmistumispaivamaara) [(str vuosi-nyt "-06-21")]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohteen-valmistumispaivamaara) [(str vuosi-nyt "-06-24")]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :takuupaivamaara) ["2024-12-31"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :toteutunuthinta) ["0"]))

           (assertoi-tr-osoite (alustan-tr-osoite alustatoimeet 0)
                               {:ajorata "1"
                                :kaista "11"
                                :tienumero "20"
                                :aosa "1"
                                :aet "1066"
                                :losa "1"
                                :let "3827"})
           (assertoi-tr-osoite (alustan-tr-osoite alustatoimeet 1)
                               {:ajorata "1"
                                :kaista "12"
                                :tienumero "20"
                                :aosa "1"
                                :aet "1066"
                                :losa "1"
                                :let "2000"})
           (assertoi-tr-osoite (alustan-tr-osoite alustatoimeet 2)
                               {:ajorata "1"
                                :kaista "12"
                                :tienumero "20"
                                :aosa "1"
                                :aet "2000"
                                :losa "1"
                                :let "2050"})
           (assertoi-tr-osoite (alustan-tr-osoite alustatoimeet 3)
                               {:ajorata "1"
                                :kaista "12"
                                :tienumero "20"
                                :aosa "1"
                                :aet "2050"
                                :losa "1"
                                :let "2100"})
           (assertoi-tr-osoite (alustan-tr-osoite alustatoimeet 4)
                               {:ajorata "1"
                                :kaista "12"
                                :tienumero "20"
                                :aosa "1"
                                :aet "2100"
                                :losa "1"
                                :let "2150"})
           (assertoi-tr-osoite (alustan-tr-osoite alustatoimeet 5)
                               {:ajorata "1"
                                :kaista "12"
                                :tienumero "20"
                                :aosa "1"
                                :aet "2150"
                                :losa "1"
                                :let "2200"})
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 0} :kasittelymenetelma)
                  ["23"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 0} :kasittelypaksuus)
                  ["10"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 0} :massamenekki)
                  nil))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 1} :kasittelymenetelma)
                  ["3"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 1} :verkkotyyppi)
                  ["1"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 1} :verkon-tarkoitus)
                  ["1"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 1} :verkon-sijainti)
                  ["1"]))
           ;; VHAR-4691 Jos alusta tp on verkko, ei saa olla massamenekkiä eikä käsittelypaksuutta
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 1} :kasittelypaksuus)
                  nil))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 1} :massamenekki)
                  nil))
           ;; HAR-4692 Jos alusta tp on AB, on lähetettävä kg/m2 tieto eli massamenekki
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 2} :massamenekki)
                  ["34"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 3} :massamenekki)
                  ["55"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 4} :massamenekki)
                  nil))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 5} :massamenekki)
                  nil))
           ;; Verkon tapauksessa tekninen toimenpide lisätään automaattisesti, ei näytetä UI:lla, arvo oltava kevyt rakenteen parantaminen
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 0} :tekninen-toimenpide)
                  ["4"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 2} :tekninen-toimenpide)
                  ["4"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 3} :tekninen-toimenpide)
                  ["9"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 4} :tekninen-toimenpide)
                  ["9"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 5} :tekninen-toimenpide)
                  ["9"]))

           (assertoi-tr-osoite (alikohteen-tr-osoite alikohteet 0)
                               {:ajorata "1"
                                :kaista "11"
                                :tienumero "20"
                                :aosa "1"
                                :aet "1066"
                                :losa "1"
                                :let "3827"})
           (assertoi-tr-osoite (alikohteen-tr-osoite alikohteet 1)
                               {:ajorata "1"
                                :kaista "12"
                                :tienumero "20"
                                :aosa "1"
                                :aet "1066"
                                :losa "1"
                                :let "3827"})
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 0} :paallystystoimenpide :uusi-paallyste)
                  ["12"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 1} :paallystystoimenpide :uusi-paallyste)
                  ["14"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 0} :paallystystoimenpide :paallystetyomenetelma)
                  ["22"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 1} :paallystystoimenpide :paallystetyomenetelma)
                  ["23"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 0} :paallystystoimenpide :raekoko)
                  ["16"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 1} :paallystystoimenpide :raekoko)
                  ["16"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 0} :paallystystoimenpide :kuulamylly)
                  ["4"])) ;; AN14
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 1} :paallystystoimenpide :kuulamylly)
                  ["2"])) ;; AN7
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 0} :paallystystoimenpide :pinta-ala)
                  ["8283"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 1} :paallystystoimenpide :pinta-ala)
                  ["8283"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 0} :paallystystoimenpide :massamenekki)
                  ["100.2"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 1} :paallystystoimenpide :massamenekki)
                  ["100.0"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 0} :paallystystoimenpide :rc-prosentti)
                  nil))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet
                                                  {:tagi :alikohde :positio 1} :paallystystoimenpide :rc-prosentti)
                  ["5"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet {:tagi :alikohde :positio 0} :tierekisteriosoitevali :kaista)
                  ["11"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet {:tagi :alikohde :positio 0} :materiaalit
                                                  {:tagi :materiaali :positio 0} :sideainetyyppi)
                  ["6"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet {:tagi :alikohde :positio 0} :materiaalit
                                                  {:tagi :materiaali :positio 0} :kiviainesesiintyman-nimi)
                  ["Kaiskakallio"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet {:tagi :alikohde :positio 0} :materiaalit
                                                  {:tagi :materiaali :positio 0} :kiviaineksen-km-arvo)
                  ["10.0"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet {:tagi :alikohde :positio 0} :materiaalit
                                                  {:tagi :materiaali :positio 0} :kiviaineksen-muotoarvo)
                  ["FI15"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet {:tagi :alikohde :positio 1} :materiaalit
                                                  {:tagi :materiaali :positio 0} :kiviaineksen-muotoarvo)
                  ["FI20"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet {:tagi :alikohde :positio 0} :materiaalit
                                                  {:tagi :materiaali :positio 0} :sideainepitoisuus)
                  ["4.8"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alikohteet {:tagi :alikohde :positio 0} :materiaalit
                                                  {:tagi :materiaali :positio 0} :lisa-aineet)
                  ["Tartuke: 0.5%"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :tienumero) ["20"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :aosa) ["1"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :aet) ["1066"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :losa) ["1"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :let) ["3827"])))
         ;; Palautetaan vastaus
         onnistunut-kirjaus-vastaus)]

      (let [onnistui? (nil? (yha/laheta-kohteet (:yha jarjestelma) urakka-id [kohde-id]))
            lahetystiedot (hae-kohteen-lahetystiedot kohde-id)]
        (is (true? onnistui?))
        (is (not (nil? (:lahetetty lahetystiedot))) "Lähetysaika on merkitty")
        (is (true? (:lahetys_onnistunut lahetystiedot))) "Lähetys on merkitty onnistuneeksi")
      (tyhjenna-kohteen-lahetystiedot kohde-id))))


(deftest tarkista-yllapitokohteen-lahetys-ilman-yha-yhteytta
  (let [kohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        onnistui? (yha/laheta-kohteet (:yha jarjestelma) urakka-id [kohde-id])
        lahetystiedot (hae-kohteen-lahetystiedot kohde-id)]
    (is (false? onnistui?))
    (is (false? (:lahetys_onnistunut lahetystiedot)) "Lähetys on merkitty epäonnistuneeksi")))

(def +xsd-polku+ "xsd/yha/")

(deftest paikkauskohteen-pot-lomakkeella-oikea-yhaid
  (let [db (luo-testitietokanta)
        odotettu-sanoma-xml (slurp "resources/xsd/yha/esimerkit/paikkauspot-toteumatietojen-kirjaus.xml")
        odotettu-xml-parsittu (xml/lue odotettu-sanoma-xml)
        _ (println "Jarno odotettu sanoma " odotettu-sanoma-xml)
        _ (println "Jarno odotettu PARSED " odotettu-xml-parsittu)
        urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        urakka (first (yha-kyselyt/hae-urakan-yhatiedot db {:urakka urakka-id}))
        urakka (assoc urakka :harjaid urakka-id
                             :sampoid (yha/yhaan-lahetettava-sampoid urakka))
        kohde-idt (q "SELECT id FROM yllapitokohde WHERE nimi = 'Pottilan AB-levityskohde'")
        kohteet (mapv #(yha/hae-kohteen-tiedot db %) kohde-idt)
        sisalto (kohteen-lahetyssanoma/muodosta urakka kohteet)
        luotu-xml-parsittu (xml/lue sisalto)
        urakka (xml/luetun-xmln-tagien-sisalto
                 luotu-xml-parsittu :urakan-kohteiden-toteumatietojen-kirjaus :urakka)
        kohde (xml/luetun-xmln-tagien-sisalto urakka :kohteet :kohde)
        tr-osoite (xml/luetun-xmln-tagin-sisalto kohde :tierekisteriosoitevali)
        virheet (xml/validoi-xml +xsd-polku+ "yha.xsd" sisalto)
        odotettu-alustarivi [{:attrs nil
                              :content [{:attrs nil
                                         :content ["2022-12-01"]
                                         :tag :karttapaivamaara}
                                        {:attrs nil
                                         :content ["4"]
                                         :tag :tienumero}
                                        {:attrs nil
                                         :content ["101"]
                                         :tag :aosa}
                                        {:attrs nil
                                         :content ["1"]
                                         :tag :aet}
                                        {:attrs nil
                                         :content ["101"]
                                         :tag :losa}
                                        {:attrs nil
                                         :content ["200"]
                                         :tag :let}
                                        {:attrs nil
                                         :content ["1"]
                                         :tag :ajorata}
                                        {:attrs nil
                                         :content ["11"]
                                         :tag :kaista}]
                              :tag :tierekisteriosoitevali}
                             {:attrs nil
                              :content ["23"]
                              :tag :kasittelymenetelma}
                             {:attrs nil
                              :content ["5"]
                              :tag :kasittelypaksuus}
                             {:attrs nil
                              :content ["4"]
                              :tag :tekninen-toimenpide}]]
    (is (= (xml/luetun-xmln-tagin-sisalto urakka :yha-id) ["868309152"]))
    (is (= (xml/luetun-xmln-tagin-sisalto urakka :harja-id) [(str urakka-id)]))


    (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdenumero) ["998"]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdetyyppi) ["1"]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdetyotyyppi) ["paallystys"]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :nimi) ["Pottilan AB-levityskohde"]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :toiden-aloituspaivamaara) ["2022-11-01"]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :paallystyksen-valmistumispaivamaara) ["2022-11-05"]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohteen-valmistumispaivamaara) [(str "2022-11-11")]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :takuupaivamaara) ["2025-11-26"]))
    ;; Todo: tätä ei vielä kaiveta paikkauskohteelle oikeasta paikasta, kaivapa se !
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :toteutunuthinta) ["5000.00"]))
    (is (= (xml/luetun-xmln-tagien-sisalto kohde :alustalle-tehdyt-toimet :alustalle-tehty-toimenpide) odotettu-alustarivi))

    (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :tienumero) ["4"]))
    (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :aosa) ["101"]))
    (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :aet) ["1"]))
    (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :losa) ["101"]))
    (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :let) ["200"]))
    (is (nil? virheet) "Ei validointivirheitä")
    (println "Jarno sisalto" sisalto)
    (println "Jarno virheet" virheet)
    (is (= odotettu-xml-parsittu luotu-xml-parsittu) "Paikkaus-POT:in XML oikein muodostettu")))