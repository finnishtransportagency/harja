(ns harja.palvelin.integraatiot.yha.urakan-kohteen-lahetys-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [taoensso.timbre :as log]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho])
  (:use [slingshot.slingshot :only [try+]]))

(def kayttaja "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :velho (component/using
             (velho/->Velho {})
             [:db :integraatioloki])
    :yha (component/using
           (yha/->Yha {:url +yha-url+})
           [:db :http-palvelin :integraatioloki :velho])))

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
  (let [kohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        urakka-yhaid (:yhaid (first (q-map (str "SELECT yhaid FROM yhatiedot WHERE urakka = " urakka-id ";"))))
        url (tee-url)
        onnistunut-kirjaus-vastaus "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<urakan-kohteiden-toteumatietojen-kirjausvastaus xmlns=\"http://www.liikennevirasto.fi/xsd/yha\">\n</urakan-kohteiden-toteumatietojen-kirjausvastaus>"]
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

           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdetyyppi) ["1"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdetyotyyppi) ["paallystys"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :nimi) ["Leppäjärven ramppi"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :toiden-aloituspaivamaara) ["2017-05-19"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :paallystyksen-valmistumispaivamaara) ["2017-05-21"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohteen-valmistumispaivamaara) ["2017-05-24"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :takuupaivamaara) ["2005-12-20"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :toteutunuthinta) ["7248.95"]))

           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :tienumero) ["20"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :aosa) ["1"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :aet) ["0"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :losa) ["3"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :let) ["0"])))
         ;; Palautetaan vastaus
         onnistunut-kirjaus-vastaus)]

      (let [onnistui? (yha/laheta-kohteet (:yha jarjestelma) urakka-id [kohde-id])
            lahetystiedot (hae-kohteen-lahetystiedot kohde-id)]
        (is (true? onnistui?))
        (is (not (nil? (:lahetetty lahetystiedot))) "Lähetysaika on merkitty")
        (is (true? (:lahetys_onnistunut lahetystiedot))) "Lähetys on merkitty onnistuneeksi")
      (tyhjenna-kohteen-lahetystiedot kohde-id))))

(deftest tarkista-yllapitokohteen-lahetys-ilman-yha-yhteytta
  (let [kohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        onnistui? (yha/laheta-kohteet (:yha jarjestelma) urakka-id [kohde-id])
        lahetystiedot (hae-kohteen-lahetystiedot kohde-id)]
    (is (false? onnistui?))
    (is (false? (:lahetys_onnistunut lahetystiedot)) "Lähetys on merkitty epäonnistuneeksi")))

