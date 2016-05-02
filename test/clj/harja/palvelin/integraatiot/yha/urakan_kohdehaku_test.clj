(ns harja.palvelin.integraatiot.yha.urakan-kohdehaku-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all])
  (:use [slingshot.slingshot :only [try+]]))

(def kayttaja "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :yha (component/using
           (yha/->Yha {:yha {:url +yha-url+}})
           [:db :http-palvelin :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest tarkista-urakoiden-haku
  (let [urakka-id (first (first (q "SELECT id FROM urakka WHERE nimi = 'YHA-päällystysurakka (sidottu)';")))
        yha-id (first (first (q (format "SELECT yhaid\nFROM yhatiedot\nWHERE urakka = %s;" urakka-id))))
        odotettu-vastaus [{:elyt ["Pohjois-Pohjanmaa"]
                           :sampotunnus "SAMPOTUNNUS1"
                           :vuodet [2016]
                           :yhaid 1
                           :yhatunnus "YHATUNNUS1"}
                          {:elyt ["Pohjois-Pohjanmaa"
                                  "Pohjois-Savo"]
                           :sampotunnus "SAMPOTUNNUS2"
                           :vuodet [2016
                                    2017]
                           :yhaid 2
                           :yhatunnus "YHATUNNUS2"}
                          {:elyt ["Pohjois-Pohjanmaa"
                                  "Pohjois-Savo"]
                           :sampotunnus "SAMPOTUNNUS3"
                           :vuodet [2016]
                           :yhaid 3
                           :yhatunnus "YHATUNNUS3"}]

        url (str +yha-url+ (format "/urakat/%s/kohteet" yha-id))]
    (with-fake-http [url +onnistunut-urakan-kohdehakuvastaus+]
      (let [vastaus (yha/hae-kohteet (:yha jarjestelma) urakka-id)]
        (is (= odotettu-vastaus vastaus))))))