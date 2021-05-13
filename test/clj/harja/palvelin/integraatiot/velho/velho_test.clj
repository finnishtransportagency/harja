(ns harja.palvelin.integraatiot.velho.velho-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho]
            [harja.tyokalut.xml :as xml]
            [harja.pvm :as pvm]))

(def kayttaja "jvh")

(def +velho-paallystystoteumat-url+ "http://localhost:1234/paallystystoteumat")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :velho (component/using
             (velho/->Velho {:paallystetoteuma-url +velho-paallystystoteumat-url+
                             :autorisaatio "abc-123"})
             [:db :http-palvelin :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest laheta-kohteet                                     ; petar todo promeni da ocekuje JSON a ne XML
  (let [kohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)
        urakka-id (hae-utajarven-paallystysurakan-id)
        urakka-yhaid (:yhaid (first (q-map (str "SELECT yhaid FROM yhatiedot WHERE urakka = " urakka-id ";"))))
        vuosi-nyt (pvm/vuosi (pvm/nyt))]
    (with-fake-http
      [{:url +velho-paallystystoteumat-url+ :method :post}
       (fn [_ {:keys [body headers]} _]
         
         (is (= "abc-123" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")

         ;; Tarkistetaan, että lähtevässä XML:ssä on oikea data
         (println "petar body = " body)

         #_(let [luettu-xml (-> (xml/lue body))
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
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :nimi) ["Ouluntie"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :toiden-aloituspaivamaara) [(str vuosi-nyt "-05-19")]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :paallystyksen-valmistumispaivamaara) [(str vuosi-nyt "-05-21")]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohteen-valmistumispaivamaara) [(str vuosi-nyt "-05-24")]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :takuupaivamaara) ["2022-12-31"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :toteutunuthinta) ["5043.95"]))

           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :tienumero) ["22"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :aosa) ["12"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :aet) ["4336"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :losa) ["12"]))
           (is (= (xml/luetun-xmln-tagin-sisalto tr-osoite :let) ["9477"])))

         "ok")]
      (is (= "ok" (:body (velho/laheta-kohteet (:velho jarjestelma) urakka-id [kohde-id])))))))
