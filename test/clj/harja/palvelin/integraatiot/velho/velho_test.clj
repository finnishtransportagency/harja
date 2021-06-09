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
(def +velho-token-url+ "http://localhost:1234/token")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :velho (component/using
             (velho/->Velho {:paallystetoteuma-url +velho-paallystystoteumat-url+
                             :token-url +velho-token-url+
                             :kayttajatunnus "abc-123"
                             :salasana "blabla"})
             [:db :http-palvelin :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest laheta-kohteet
  (let [kohde-id (:id (first (q-map (str "SELECT id FROM yllapitokohde WHERE nimi = 'Tärkeä kohde mt20'"))))
        pyynnot (atom [])
        urakka-id (hae-utajarven-paallystysurakan-id)
        urakka-yhaid (:yhaid (first (q-map (str "SELECT yhaid FROM yhatiedot WHERE urakka = " urakka-id ";"))))]
    (with-fake-http
      [{:url +velho-token-url+ :method :post}
       (fn [_ {:keys [body headers]} _]
         (swap! pyynnot conj {:headers headers :body body})
         "{\"access_token\":\"TEST_TOKEN\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")

       {:url +velho-paallystystoteumat-url+ :method :post}
       (fn [_ {:keys [body headers]} _]
         (is (= "Bearer TEST_TOKEN" (get headers "Authorization")) "Oikeaa autorisaatio otsikkoa ei käytetty")
         (swap! pyynnot conj {:headers headers :body body})
         "ok")]

      (velho/laheta-kohteet (:velho jarjestelma) urakka-id [kohde-id])

      (is (= 9 (count @pyynnot)) "Kokonaan täytyy olla 9 pyyntöä: token + 2 päällystekerrosta + 2 alustaa"))))
