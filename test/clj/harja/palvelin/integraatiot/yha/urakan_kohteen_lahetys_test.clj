(ns harja.palvelin.integraatiot.yha.urakan-kohteen-lahetys-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [taoensso.timbre :as log]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all])
  (:use [slingshot.slingshot :only [try+]]))

(def kayttaja "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :yha (component/using
           (yha/->Yha {:url +yha-url+})
           [:db :http-palvelin :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn hae-urakka-id [kohde-id]
  (first (first (q (format "SELECT urakka FROM yllapitokohde WHERE id = %s;" kohde-id)))))

(defn tee-url []
  (str +yha-url+ "toteumatiedot"))

(defn hae-kohteen-lahetystiedot [kohde-id]
  (let [tila (first (q (format "SELECT lahetetty, lahetys_onnistunut FROM yllapitokohde WHERE id = %s" kohde-id)))]
    {:lahetetty (first tila)
     :lahetys_onnistunut (second tila)}))

(defn tyhjenna-kohteen-lahetystiedot [kohde-id]
  (u (format "UPDATE yllapitokohde SET lahetetty = NULL, lahetys_onnistunut = NULL WHERE id = %s" kohde-id)))

(deftest tarkista-yllapitokohteen-lahetys
  (let [kohde-id 1
        urakka-id (hae-urakka-id 1)
        url (tee-url)
        onnistunut-kirjaus-vastaus "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<urakan-kohteiden-toteumatietojen-kirjausvastaus xmlns=\"http://www.liikennevirasto.fi/xsd/yha\">\n</urakan-kohteiden-toteumatietojen-kirjausvastaus>"]
    (with-fake-http [{:url url :method :post}
                     (fn [_ opts _]
                       ;; TODO Lähetetty request kaipailisi testejä (arvot lähetetään oikein)
                       (is (= url (:url opts)) "Kutsu tehdään oikeaan osoitteeseen")
                       onnistunut-kirjaus-vastaus)]

      (let [onnistui? (yha/laheta-kohteet (:yha jarjestelma) urakka-id [kohde-id])
            lahetystiedot (hae-kohteen-lahetystiedot kohde-id)]
        (is (true? onnistui?))
        (is (not (nil? (:lahetetty lahetystiedot))) "Lähetysaika on merkitty")
        (is (true? (:lahetys_onnistunut lahetystiedot))) "Lähetys on merkitty onnistuneeksi")
      (tyhjenna-kohteen-lahetystiedot kohde-id))))

(deftest tarkista-yllapitokohteen-lahetys-ilman-yha-yhteytta
  (let [kohde-id 1
        urakka-id (hae-urakka-id 1)
        onnistui? (yha/laheta-kohteet (:yha jarjestelma) urakka-id [kohde-id])
        lahetystiedot (hae-kohteen-lahetystiedot kohde-id)]
    (is (false? onnistui?))
    (is (false? (:lahetys_onnistunut lahetystiedot)) "Lähetys on merkitty epäonnistuneeksi")))

