(ns harja.palvelin.integraatiot.yha.urakan-kohteen-lahetys-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.pvm :as pvm])
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
        urakka-id (hae-utajarven-paallystysurakan-id)
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

(deftest tarkista-yllapitokohteen-lahetys-pot2
  (let [kohde-id (hae-yllapitokohde-tarkea-kohde-pot2)
        urakka-id (hae-utajarven-paallystysurakan-id)
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
               alustatoimeet (xml/luetun-xmln-tagien-sisalto kohde :alustalle-tehdyt-toimet)
               alikohteet (xml/luetun-xmln-tagien-sisalto kohde :alikohteet)
               alikohde-0-tr-osoite (xml/luetun-xmln-tagien-sisalto alikohteet {:tagi :alikohde :positio 0} :tierekisteriosoitevali)
               alikohde-1-tr-osoite (xml/luetun-xmln-tagien-sisalto alikohteet {:tagi :alikohde :positio 1} :tierekisteriosoitevali)
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
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 0} :kasittelymenetelma)
                  ["23"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 0} :kasittelypaksuus)
                  ["10"]))
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
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 0} :tekninen-toimenpide)
                  ["4"]))
           (assertoi-tr-osoite alikohde-0-tr-osoite {:ajorata "1"
                                                     :kaista "11"
                                                     :tienumero "20"
                                                     :aosa "1"
                                                     :aet "1066"
                                                     :losa "1"
                                                     :let "3827"})
           (assertoi-tr-osoite alikohde-1-tr-osoite {:ajorata "1"
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
                                                  {:tagi :alikohde :positio 0} :paallystystoimenpide :massamenekki)
                  ["333"]))
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
                  ["9.5"]))
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
        urakka-id (hae-utajarven-paallystysurakan-id)
        onnistui? (yha/laheta-kohteet (:yha jarjestelma) urakka-id [kohde-id])
        lahetystiedot (hae-kohteen-lahetystiedot kohde-id)]
    (is (false? onnistui?))
    (is (false? (:lahetys_onnistunut lahetystiedot)) "Lähetys on merkitty epäonnistuneeksi")))

