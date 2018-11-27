(ns harja.palvelin.integraatiot.velho.paallystysilmoitusten-lahetys-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho])
  (:use [slingshot.slingshot :only [try+]]))

(def kayttaja "jvh")

(def +velho-paallystetoteumat-url+ "http://localhost:1234/paallystetoteumat")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :velho (component/using
             (velho/->Velho {:paallystetoteumat-url +velho-paallystetoteumat-url+})
             [:db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest tarkista-yllapitokohteen-lahetys
  (let [kohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        urakka-id (hae-muhoksen-paallystysurakan-id)
        onnistunut-kirjaus-vastaus ""]
    (with-fake-http
      [{:url +velho-paallystetoteumat-url+ :method :post}
       (fn [_ {:keys [url body] :as opts} _]
         (is (= url (:url opts)) "Kutsu tehdään oikeaan osoitteeseen")
         ;; Tarkistetaan, että lähtevässä XML:ssä on oikea data
         (let [luettu-xml (-> (xml/lue body))
               urakka (xml/luetun-xmln-tagien-sisalto
                        luettu-xml
                        :urakan-kohteiden-toteumatietojen-kirjaus :urakka)
               kohde (xml/luetun-xmln-tagien-sisalto
                       urakka
                       :kohteet :kohde)]
           (is (= (xml/luetun-xmln-tagin-sisalto urakka :harja-id) [(str urakka-id)]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :kohdetyotyyppi) ["paallystys"]))
           (is (= (xml/luetun-xmln-tagin-sisalto kohde :nimi) ["Leppäjärven ramppi"]))
           (is (not (nil? (xml/luetun-xmln-tagien-sisalto kohde :alikohteet :alikohde :geometria)))))
         ;; Palautetaan vastaus
         onnistunut-kirjaus-vastaus)]

      (let [onnistui? (velho/laheta-paallystysilmoitukset (:velho jarjestelma) urakka-id [kohde-id])]
        (is (true? onnistui?))))))

