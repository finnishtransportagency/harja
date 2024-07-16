(ns harja.palvelin.integraatiot.yha.urakan-kohteen-lahetys-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.kyselyt.yha :as yha-kyselyt]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma :as kohteen-lahetyssanoma]
            [harja.palvelin.integraatiot.yha.kohteen-lahetyssanoma-test :as kohteen-lahetyssanoma-test]
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

           (is (= (xml/luetun-xmln-tagin-sisalto kohde :yha-id) ["13371"]))
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

(defn- kulutuskerroksen-tr-osoite [kulutuskerrokselle-tehdyt-toimet positio]
  (xml/luetun-xmln-tagien-sisalto kulutuskerrokselle-tehdyt-toimet {:tagi :kulutuskerrokselle-tehty-toimenpide :positio positio} :tierekisteriosoitevali))

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
               kulutuskerrokselle-tehdyt-toimet (xml/luetun-xmln-tagien-sisalto kohde :kulutuskerrokselle-tehdyt-toimet)
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
           ;; Jos alusta tp on verkko, ei saa olla massamenekkiä eikä käsittelypaksuutta
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 1} :kasittelypaksuus)
                  nil))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 1} :massamenekki)
                  nil))
           ;; Jos alusta tp on AB, on lähetettävä kg/m2 tieto eli massamenekki
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 2} :massamenekki)
                  ["34.00"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 3} :massamenekki)
                  ["55.00"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 4} :massamenekki)
                  nil))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 5} :massamenekki)
                  nil))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 0} :kasittelymenetelma)
                  ["23"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 2} :kasittelymenetelma)
                  ["2"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 3} :kasittelymenetelma)
                  ["32"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 4} :kasittelymenetelma)
                  ["31"]))
           (is (= (xml/luetun-xmln-tagien-sisalto alustatoimeet
                                                  {:tagi :alustalle-tehty-toimenpide :positio 5} :kasittelymenetelma)
                  ["41"]))
           (assertoi-tr-osoite (kulutuskerroksen-tr-osoite kulutuskerrokselle-tehdyt-toimet 0)
             {:ajorata "1"
              :kaista "11"
              :tienumero "20"
              :aosa "1"
              :aet "1066"
              :losa "1"
              :let "3827"})
           
           (assertoi-tr-osoite (kulutuskerroksen-tr-osoite kulutuskerrokselle-tehdyt-toimet 1)
             {:ajorata "1"
              :kaista "12"
              :tienumero "20"
              :aosa "1"
              :aet "1066"
              :losa "1"
              :let "3827"}) 
           
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


(deftest tarkista-yllapitokohteen-lahetys-ilman-http
  (let [kohde-id (ffirst (q "SELECT id FROM yllapitokohde WHERE nimi = 'Kirkonkylä - Toppinen 2'"))
        urakka-id (hae-urakan-id-nimella "POT2 testipäällystysurakka")
        onnistui? (yha/laheta-kohteet (:yha jarjestelma) urakka-id [kohde-id])
        lahetystiedot (hae-kohteen-lahetystiedot kohde-id)]
    (is (false? onnistui?))
    (is (false? (:lahetys_onnistunut lahetystiedot)) "Lähetys on merkitty epäonnistuneeksi")))

(def +xsd-polku+ "xsd/yha/")

(def sisalto-tulos
   [:urakan-kohteiden-toteumatietojen-kirjaus
   {:xmlns "http://www.vayla.fi/xsd/yha"}
   [:urakka
    [:yha-id 5731290]
    [:harja-id 53]
    [:sampotunnus "5731290-TES2"]
    [:tunnus "YHA5731290"]
    [:kohteet
     [:kohde
      [:yha-id 123456]
      [:harja-id 32]
      [:kohdenumero 1]
      [:kohdetyyppi 1]
      [:kohdetyotyyppi "paallystys"]
      [:nimi "Kirkonkylä - Toppinen 2"]
      [:tunnus "a"]
      [:toiden-aloituspaivamaara "2023-01-01"]
      [:paallystyksen-valmistumispaivamaara "2023-08-01"]
      [:kohteen-valmistumispaivamaara "2023-08-01"]
      [:takuupaivamaara "2023-08-01"]
      [:toteutunuthinta 98900M]
      [:tierekisteriosoitevali
       [:karttapaivamaara "2022-07-15"]
       [:tienumero 86]
       [:aosa 20]
       [:aet 0]
       [:losa 20]
       [:let 1300]
       nil
       nil]
      [:alustalle-tehdyt-toimet
       [:alustalle-tehty-toimenpide
        [:harja-id 9]
        [:tierekisteriosoitevali
         [:karttapaivamaara "2022-07-15"]
         [:tienumero 86]
         [:aosa 20]
         [:aet 0]
         [:losa 20]
         [:let 650]
         [:ajorata 1]
         [:kaista 11]]
        [:kasittelymenetelma 32]
        [:lisatty-paksuus 12]
        [:kasittelysyvyys 300]
        [:verkkotyyppi 5]
        nil
        nil
        [:massamenekki 0.10M]
        [:kokonaismassamaara 10.2M]
        [:massa
         [:massatyyppi 12]
         [:max-raekoko 16]
         [:kuulamyllyluokka 3]
         [:yhteenlaskettu-kuulamyllyarvo 10.0M]
         [:yhteenlaskettu-litteysluku 20.0M]
         [:litteyslukuluokka "FI15"]
         [:runkoaineet
          [:runkoaine
           [:runkoainetyyppi 1]
           [:kuulamyllyarvo 10.0M]
           [:litteysluku 20.0M]
           [:massaprosentti 100.0M]
           nil
           nil]
          [:runkoaine
           [:runkoainetyyppi 3]
           nil
           nil
           [:massaprosentti 1.0M]
           [:fillerityyppi "Kalkkifilleri (KF)"]
           nil]]
         [:sideaineet [:sideaine [:tyyppi 1] [:pitoisuus 5.5M]]]
         [:lisaaineet [:lisaaine [:tyyppi 1] [:pitoisuus 0.5M]]]]
        [:murske
         [:mursketyyppi 1]
         [:rakeisuus "0/40"]
         [:iskunkestavyys "LA30"]]]
       [:alustalle-tehty-toimenpide
        [:harja-id 10]
        [:tierekisteriosoitevali
         [:karttapaivamaara "2022-07-15"]
         [:tienumero 86]
         [:aosa 20]
         [:aet 650]
         [:losa 20]
         [:let 1300]
         [:ajorata 1]
         [:kaista 11]]
        [:kasittelymenetelma 32]
        [:lisatty-paksuus 12]
        [:kasittelysyvyys 300]
        [:verkkotyyppi 5]
        nil
        nil
        [:massamenekki 0.10M]
        [:kokonaismassamaara 10.2M]
        [:massa
         [:massatyyppi 12]
         [:max-raekoko 16]
         [:kuulamyllyluokka 3]
         [:yhteenlaskettu-kuulamyllyarvo 10.0M]
         [:yhteenlaskettu-litteysluku 20.0M]
         [:litteyslukuluokka "FI15"]
         [:runkoaineet
          [:runkoaine
           [:runkoainetyyppi 1]
           [:kuulamyllyarvo 10.0M]
           [:litteysluku 20.0M]
           [:massaprosentti 100.0M]
           nil
           nil]
          [:runkoaine
           [:runkoainetyyppi 3]
           nil
           nil
           [:massaprosentti 1.0M]
           [:fillerityyppi "Kalkkifilleri (KF)"]
           nil]]
         [:sideaineet [:sideaine [:tyyppi 1] [:pitoisuus 5.5M]]]
         [:lisaaineet [:lisaaine [:tyyppi 1] [:pitoisuus 0.5M]]]]
        [:murske
         [:mursketyyppi 1]
         [:rakeisuus "0/40"]
         [:iskunkestavyys "LA30"]]]]
      [:kulutuskerrokselle-tehdyt-toimet
       [:kulutuskerrokselle-tehty-toimenpide
        [:yha-id 123457]
        [:harja-id 45]
        [:poistettu false]
        [:tierekisteriosoitevali
         [:karttapaivamaara "2022-07-15"]
         [:tienumero 86]
         [:aosa 20]
         [:aet 0]
         [:losa 20]
         [:let 650]
         [:ajorata 1]
         [:kaista 11]]
        [:leveys 4.00M]
        [:pinta-ala 2600M]
        [:paallystetyomenetelma 21]
        [:massamenekki 100.0M]
        [:kokonaismassamaara 260M]
        [:massa
         [:massatyyppi 12]
         [:max-raekoko 16]
         [:kuulamyllyluokka 3]
         nil
         nil
         [:litteyslukuluokka "FI15"]
         [:runkoaineet
          [:runkoaine
           [:runkoainetyyppi 1]
           [:kuulamyllyarvo 10.0M]
           [:litteysluku 20.0M]
           [:massaprosentti 100.0M]
           nil
           nil]
          [:runkoaine
           [:runkoainetyyppi 3]
           nil
           nil
           [:massaprosentti 1.0M]
           [:fillerityyppi "Kalkkifilleri (KF)"]
           nil]]
         [:sideaineet [:sideaine [:tyyppi 1] [:pitoisuus 5.5M]]]
         [:lisaaineet [:lisaaine [:tyyppi 1] [:pitoisuus 0.5M]]]]]
       [:kulutuskerrokselle-tehty-toimenpide
        [:yha-id 123458]
        [:harja-id 46]
        [:poistettu false]
        [:tierekisteriosoitevali
         [:karttapaivamaara "2022-07-15"]
         [:tienumero 86]
         [:aosa 20]
         [:aet 650]
         [:losa 20]
         [:let 1300]
         [:ajorata 1]
         [:kaista 11]]
        [:leveys 4.00M]
        [:pinta-ala 2600M]
        [:paallystetyomenetelma 21]
        [:massamenekki 100.0M]
        [:kokonaismassamaara 260M]
        [:massa
         [:massatyyppi 12]
         [:max-raekoko 16]
         [:kuulamyllyluokka 3]
         nil
         nil
         [:litteyslukuluokka "FI15"]
         [:runkoaineet
          [:runkoaine
           [:runkoainetyyppi 1]
           [:kuulamyllyarvo 10.0M]
           [:litteysluku 20.0M]
           [:massaprosentti 100.0M]
           nil
           nil]
          [:runkoaine
           [:runkoainetyyppi 3]
           nil
           nil
           [:massaprosentti 1.0M]
           [:fillerityyppi "Kalkkifilleri (KF)"]
           nil]]
         [:sideaineet [:sideaine [:tyyppi 1] [:pitoisuus 5.5M]]]
         [:lisaaineet [:lisaaine [:tyyppi 1] [:pitoisuus 0.5M]]]]]]]]]])

(deftest tarkista-kohteen-lahetyksen-sisalto
  (let [db (luo-testitietokanta)
        odotettu-sanoma-xml (slurp "resources/xsd/yha/esimerkit/paikkauspot2-toteumatietojen-kirjaus.xml") 
        odotettu-xml-parsittu (xml/lue odotettu-sanoma-xml)
        kohde-idt (q "SELECT id FROM yllapitokohde WHERE nimi = 'Kirkonkylä - Toppinen 2'") 
        urakka-id (hae-urakan-id-nimella "POT2 testipäällystysurakka")
        urakka (first (yha-kyselyt/hae-urakan-yhatiedot db {:urakka urakka-id}))
        urakka (assoc urakka :harjaid urakka-id
                 :sampoid (yha/yhaan-lahetettava-sampoid urakka))
        kohteet (mapv #(yha/hae-kohteen-tiedot-pot2 db %) kohde-idt)
        lahetys-avaimet-alustalle-tehdyt-toimet (set (keys (first (:alustalle-tehdyt-toimet (first kohteet))))) 
        lahetys-avaimet-kulutuskerrokselle-tehdyt-toimet (set (keys (first (:kulutuskerrokselle-tehdyt-toimet (first kohteet)))))
        kulutuskerros-testi-avaimet (set (keys kohteen-lahetyssanoma-test/testi-kulutuskerrokselle-tehdyt-toimet))
        alusta-testi-avaimet (set (keys kohteen-lahetyssanoma-test/testi-alustalle-tehdyt-toimet)) 
        sisalto (kohteen-lahetyssanoma/muodosta-sanoma urakka kohteet)
        xml (kohteen-lahetyssanoma/muodosta urakka kohteet)
        luotu-xml-parsittu (xml/lue xml)]
    (is (= #{} (clojure.set/difference alusta-testi-avaimet lahetys-avaimet-alustalle-tehdyt-toimet)) "Alustan kaikki avaimet mukana")
    (is (= #{} (clojure.set/difference kulutuskerros-testi-avaimet lahetys-avaimet-kulutuskerrokselle-tehdyt-toimet)) "Kulutuskerroksen kaikki avaimet mukana")
    (is (= sisalto-tulos sisalto) "Sisältö ei ole muuttunut")
    (is (xml/validi-xml? "xsd/yha/" "yha2.xsd" xml) "Muodostettu XML on validia")
    (is (= odotettu-xml-parsittu luotu-xml-parsittu) "Paikkaus-POT:in XML oikein muodostettu")))


(deftest paikkauskohteen-pot-lomakkeella-oikea-yhaid
  (let [db (luo-testitietokanta)
        odotettu-sanoma-xml (slurp "resources/xsd/yha/esimerkit/paikkauspot-toteumatietojen-kirjaus.xml")
        odotettu-xml-parsittu (xml/lue odotettu-sanoma-xml)
        urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        urakka (first (yha-kyselyt/hae-urakan-yhatiedot db {:urakka urakka-id}))
        urakka (assoc urakka :harjaid urakka-id
                             :sampoid (yha/yhaan-lahetettava-sampoid urakka))
        kohde-idt (q "SELECT id FROM yllapitokohde WHERE nimi = 'Pottilan AB-levityskohde'")
        kohteet (mapv #(yha/hae-kohteen-tiedot-pot2 db %) kohde-idt)
        sisalto (kohteen-lahetyssanoma/muodosta urakka kohteet)
        luotu-xml-parsittu (xml/lue sisalto)
        urakka (xml/luetun-xmln-tagien-sisalto
                 luotu-xml-parsittu :urakan-kohteiden-toteumatietojen-kirjaus :urakka)
        kohde (xml/luetun-xmln-tagien-sisalto urakka :kohteet :kohde)
        tr-osoite (xml/luetun-xmln-tagin-sisalto kohde :tierekisteriosoitevali)
        virheet (xml/validoi-xml +xsd-polku+ "yha2.xsd" sisalto)
        odotettu-alustarivi [{:tag :harja-id, :attrs nil, :content ["7"]}
                             {:tag :tierekisteriosoitevali,
                              :attrs nil,
                              :content
                              [{:tag :karttapaivamaara, :attrs nil, :content ["2022-12-13"]}
                               {:tag :tienumero, :attrs nil, :content ["4"]}
                               {:tag :aosa, :attrs nil, :content ["101"]}
                               {:tag :aet, :attrs nil, :content ["1"]}
                               {:tag :losa, :attrs nil, :content ["101"]}
                               {:tag :let, :attrs nil, :content ["200"]}
                               {:tag :ajorata, :attrs nil, :content ["1"]}
                               {:tag :kaista, :attrs nil, :content ["11"]}]}
                             {:tag :kasittelymenetelma, :attrs nil, :content ["23"]}
                             {:tag :lisatty-paksuus, :attrs nil, :content ["5"]}
                             {:tag :murske,
                              :attrs nil,
                              :content
                              [{:tag :mursketyyppi, :attrs nil, :content ["1"]}
                               {:tag :rakeisuus, :attrs nil, :content ["0/40"]}
                               {:tag :iskunkestavyys, :attrs nil, :content ["LA30"]}]}]]
    (is (= (xml/luetun-xmln-tagin-sisalto urakka :yha-id) ["868309152"]))
    (is (= (xml/luetun-xmln-tagin-sisalto urakka :harja-id) [(str urakka-id)]))


    (is (nil? (xml/luetun-xmln-tagin-sisalto kohde :kohdenumero)))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :yha-id) [(str kohteen-lahetyssanoma/paikkauskohteiden-yha-id)]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdetyyppi) ["1"]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdetyotyyppi) ["paallystys"]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :nimi) ["Pottilan AB-levityskohde"]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :toiden-aloituspaivamaara) ["2022-11-01"]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :paallystyksen-valmistumispaivamaara) ["2022-11-05"]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohteen-valmistumispaivamaara) [(str "2022-11-11")]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :takuupaivamaara) ["2025-11-05"]))
    (is (= (xml/luetun-xmln-tagin-sisalto kohde :toteutunuthinta) ["5000"]))
    (is (= (xml/luetun-xmln-tagien-sisalto kohde :alustalle-tehdyt-toimet :alustalle-tehty-toimenpide) odotettu-alustarivi))

    (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :tienumero) ["4"]))
    (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :aosa) ["101"]))
    (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :aet) ["1"]))
    (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :losa) ["101"]))
    (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :let) ["200"]))
    (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :karttapaivamaara) ["2022-12-13"]))
    (is (nil? virheet) "Ei validointivirheitä")
    (is (= odotettu-xml-parsittu luotu-xml-parsittu) "Paikkaus-POT:in XML oikein muodostettu")))
