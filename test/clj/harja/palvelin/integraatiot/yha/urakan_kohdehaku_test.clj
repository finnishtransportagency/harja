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
           (yha/->Yha {:url +yha-url+})
           [:db :http-palvelin :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn hae-urakka-id []
  (first (first (q "SELECT id FROM urakka WHERE nimi = 'YHA-päällystysurakka (sidottu)';"))))

(defn hae-yha-id [urakka-id]
  (first (first (q (format "SELECT yhaid\nFROM yhatiedot\nWHERE urakka = %s;" urakka-id)))))

(defn tee-url []
  (str +yha-url+ "haeUrakanKohteet"))

(deftest tarkista-urakan-kohteiden-haku
  (let [urakka-id (hae-urakka-id)
        odotettu-vastaus [{:alikohteet [{:paallystystoimenpide {:kokonaismassamaara 124.0
                                                                :kuulamylly 4
                                                                :paallystetyomenetelma 22
                                                                :raekoko 12
                                                                :rc-prosentti 14
                                                                :uusi-paallyste 11}
                                         :tierekisteriosoitevali {:aet 3
                                                                  :ajorata 0
                                                                  :aosa 3
                                                                  :kaista 11
                                                                  :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
                                                                  :let 3
                                                                  :losa 3
                                                                  :tienumero 3}
                                         :tunnus "A"
                                         :yha-id 3}
                                        {:paallystystoimenpide {:kokonaismassamaara 124.0
                                                                :kuulamylly 4
                                                                :paallystetyomenetelma 22
                                                                :raekoko 12
                                                                :rc-prosentti 14
                                                                :uusi-paallyste 11}
                                         :tierekisteriosoitevali {:aet 3
                                                                  :ajorata 0
                                                                  :aosa 3
                                                                  :kaista 11
                                                                  :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
                                                                  :let 3
                                                                  :losa 3
                                                                  :tienumero 3}
                                         :tunnus "B"
                                         :yha-id 4}]
                           :keskimaarainen-vuorokausiliikenne 1000
                           :nimi "string"
                           :nykyinen-paallyste 1
                           :tierekisteriosoitevali {:aet 3
                                                    :ajorata 0
                                                    :aosa 3
                                                    :kaista 11
                                                    :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
                                                    :let 3
                                                    :losa 3
                                                    :tienumero 3}
                           :yha-id 3
                           :yha-kohdenumero 666
                           :yllapitokohdetyotyyppi :paikkaus
                           :yllapitokohdetyyppi "paallyste"
                           :yllapitoluokka 1}]
        url (tee-url)]
    (with-fake-http [url +onnistunut-urakan-kohdehakuvastaus+]
      (let [vastaus (yha/hae-kohteet (:yha jarjestelma) urakka-id "testi")]
        (is (= odotettu-vastaus vastaus))))))

(deftest tarkista-epaonnistunut-kutsu
  (let [urakka-id (hae-urakka-id)
        yha-id (hae-yha-id urakka-id)]
    (with-fake-http [{:url (tee-url) :method :get} 500]
      (is (thrown? Exception (yha/hae-kohteet (:yha jarjestelma) yha-id ""))
          "Poikkeusta ei heitetty epäonnistuneesta kutsusta."))))

(deftest tarkista-virhevastaus
  (let [urakka-id (hae-urakka-id)]
    (with-fake-http [(tee-url) +virhevastaus+]
      (try+
        (yha/hae-kohteet (:yha jarjestelma) urakka-id "testi")
        (is false "Poikkeusta ei heitetty epäonnistuneesta kutsusta.")
        (catch [:type yha/+virhe-urakan-kohdehaussa+] {:keys [virheet]}
          (is true "Poikkeus heitettiin epäonnistuneesta kutsusta."))))))