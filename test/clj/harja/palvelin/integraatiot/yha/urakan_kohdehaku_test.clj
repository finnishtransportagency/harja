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

(defn tee-url [yha-id]
  (str +yha-url+ (format "urakat/%s/kohteet" yha-id)))

#_(deftest tarkista-urakan-kohteiden-haku
  (let [urakka-id (hae-urakka-id)
        yha-id (hae-yha-id urakka-id)
        odotettu-vastaus[{:alikohteet [{:paallystystoimenpide {:kokonaismassamaara 2
                                                               :kuulamylly 3
                                                               :paallystetyomenetelma 31
                                                               :raekoko 12
                                                               :rc-prosentti 80
                                                               :uusi-paallyste 21}
                                        :tierekisteriosoitevali {:aet 0
                                                                 :ajorata 0
                                                                 :aosa 36
                                                                 :kaista 1
                                                                 :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
                                                                 :let 0
                                                                 :losa 41
                                                                 :tienumero 66}
                                        :tunnus nil
                                        :yha-id 254915666}
                                       {:paallystystoimenpide {:kokonaismassamaara 1
                                                               :kuulamylly 1
                                                               :paallystetyomenetelma 21
                                                               :raekoko 10
                                                               :rc-prosentti 1
                                                               :uusi-paallyste 21}
                                        :tierekisteriosoitevali {:aet 0
                                                                 :ajorata 0
                                                                 :aosa 41
                                                                 :kaista 1
                                                                 :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
                                                                 :let 2321
                                                                 :losa 41
                                                                 :tienumero 66}
                                        :tunnus nil
                                        :yha-id 254915667}]
                          :keskimaarainen-vuorokausiliikenne 2509
                          :kohdetyyppi :paallystys
                          :nykyinen-paallyste 1
                          :tierekisteriosoitevali {:aet 0
                                                   :ajorata 0
                                                   :aosa 36
                                                   :kaista 1
                                                   :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
                                                   :let 2321
                                                   :losa 41
                                                   :tienumero 66}
                          :tunnus "kauhajoen suora"
                          :yha-id 251041528
                          :yllapitoluokka 3}
                         {:alikohteet [{:paallystystoimenpide {:kokonaismassamaara 1
                                                               :kuulamylly 1
                                                               :paallystetyomenetelma 21
                                                               :raekoko 10
                                                               :rc-prosentti 1
                                                               :uusi-paallyste 21}
                                        :tierekisteriosoitevali {:aet 450
                                                                 :ajorata 0
                                                                 :aosa 230
                                                                 :kaista 1
                                                                 :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
                                                                 :let 460
                                                                 :losa 230
                                                                 :tienumero 3}
                                        :tunnus nil
                                        :yha-id 254915669}]
                          :keskimaarainen-vuorokausiliikenne 3107
                          :kohdetyyppi :paallystys
                          :nykyinen-paallyste 1
                          :tierekisteriosoitevali {:aet 450
                                                   :ajorata 0
                                                   :aosa 230
                                                   :kaista 1
                                                   :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
                                                   :let 460
                                                   :losa 230
                                                   :tienumero 3}
                          :tunnus "asdf"
                          :yha-id 251603670
                          :yllapitoluokka 1}]
        ;; todo: palauta tämä vastaus, kun YHA-yhteys saadaan toimimaan
        #_[{:alikohteet [{:paallystystoimenpide {:kokonaismassamaara 124
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
                                         :yha-id 3}]
                           :keskimaarainen-vuorokausiliikenne 1000
                           :kohdetyyppi :paikkaus
                           :nykyinen-paallyste 1
                           :tierekisteriosoitevali {:aet 3
                                                    :ajorata 0
                                                    :aosa 3
                                                    :kaista 11
                                                    :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
                                                    :let 3
                                                    :losa 3
                                                    :tienumero 3}
                           :tunnus "string"
                           :yha-id 5
                           :yllapitoluokka 1}]

        url (tee-url yha-id)]
    (with-fake-http [url +onnistunut-urakan-kohdehakuvastaus+]
      (let [vastaus (yha/hae-kohteet (:yha jarjestelma) urakka-id)]
        (is (= odotettu-vastaus vastaus))))))

(deftest tarkista-epaonnistunut-kutsu
  (let [urakka-id (hae-urakka-id)
        yha-id (hae-yha-id urakka-id)]
    (with-fake-http [{:url (tee-url yha-id) :method :get} 500]
      (is (thrown? Exception (yha/hae-kohteet (:yha jarjestelma) yha-id ""))
          "Poikkeusta ei heitetty epäonnistuneesta kutsusta."))))

;; todo: palauta, kun oikea YHA-yhteys on saatu
#_(deftest tarkista-virhevastaus
    (let [urakka-id (hae-urakka-id)
          yha-id (hae-yha-id urakka-id)]
      (with-fake-http [(tee-url yha-id) +virhevastaus+]
        (try+
          (yha/hae-kohteet (:yha jarjestelma) urakka-id)
          (is false "Poikkeusta ei heitetty epäonnistuneesta kutsusta.")
          (catch [:type yha/+virhe-urakan-kohdehaussa+] {:keys [virheet]}
            (is true "Poikkeus heitettiin epäonnistuneesta kutsusta."))))))