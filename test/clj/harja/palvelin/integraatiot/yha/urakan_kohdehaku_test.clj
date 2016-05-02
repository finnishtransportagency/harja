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

        url (str +yha-url+ (format "/urakat/%s/kohteet" urakka-id))]
    (with-fake-http [url +onnistunut-urakoiden-hakuvastaus+]
      (let [vastaus (yha/hae-urakat (:yha jarjestelma) "tunniste" "sampoid" 2016)]
        (is (= odotettu-vastaus vastaus))))))

;; todo: palauta tämä vastaus, kun oikea YHA-yhteys otetaan käyttöön
#_(deftest tarkista-epaonnistunut-kutsu)
  (with-fake-http [{:url (str +yha-url+ "/urakkahaku") :method :get} 500]
    (is (thrown? Exception (yha/hae-urakat (:yha jarjestelma) "tunniste" "sampoid" 2016))
        "Poikkeusta ei heitetty epäonnistuneesta kutsusta."))

;; todo: palauta tämä vastaus, kun oikea YHA-yhteys otetaan käyttöön
#_ (deftest tarkista-virhevastaus)
  (let [url (str +yha-url+ "/urakkahaku")]
    (with-fake-http [url +urakkahaun-virhevastaus+]
      (try+
        (yha/hae-urakat (:yha jarjestelma) "tunniste" "sampoid" 2016)
        (is false "Poikkeusta ei heitetty epäonnistuneesta kutsusta.")
        (catch [:type yha/+virhe-urakoiden-haussa+] {:keys [virheet]}
          (is true "Poikkeus heitettiin epäonnistuneesta kutsusta.")))))