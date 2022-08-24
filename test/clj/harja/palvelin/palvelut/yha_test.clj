(ns harja.palvelin.palvelut.yha-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :refer :all]
            [harja.palvelin.integraatiot.yha.urakan-kohdehaku-test :as urakan-kohdehaku-test]
            [harja.palvelin.integraatiot.yha.urakoiden-haku-test :as urakoiden-haku-test]
            [harja.palvelin.palvelut.yllapitokohteet :refer :all]
            [harja.testi :refer :all]
            [clojure.core.match :refer [match]]
            [harja.jms-test :refer [feikki-jms]]
            [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<!! timeout]]
            [harja.palvelin.palvelut.yha :as yha]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha-integraatio]
            [harja.kyselyt.yha :as yha-kyselyt]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.kyselyt.konversio :as konv])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil) [:db])
                        :yha-integraatio (component/using
                                           (yha-integraatio/->Yha {:url +yha-url+})
                                           [:db :integraatioloki])
                        :yha (component/using
                               (yha/->Yha)
                               [:http-palvelin :db :yha-integraatio])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest sido-yha-urakka-harja-urakkaan
  (let [urakka-id (hae-urakan-id-nimella "YHA-päällystysurakka")
        yhatiedot-ennen-testia (ffirst (q "SELECT id FROM yhatiedot WHERE urakka = " urakka-id ";"))]
    (is (nil? yhatiedot-ennen-testia) "Urakan yhatiedot on tyhjä ennen testiä")

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :sido-yha-urakka-harja-urakkaan +kayttaja-jvh+
                    {:harja-urakka-id urakka-id
                     :yha-tiedot {:yhatunnus "YHATUNNUS"
                                  :yhaid 666
                                  :yhanimi "YHANIMI"}})

    (let [yhatiedot-testin-jalkeen (ffirst (q "SELECT id FROM yhatiedot WHERE urakka = " urakka-id ";"))]
      (is (integer? yhatiedot-testin-jalkeen) "Urakka sidottiin YHA-urakkaan oikein"))))

(deftest ala-anna-vaihtaa-lukittua-sidontaa
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        yhatiedot-ennen-testia (ffirst (q "SELECT id FROM yhatiedot WHERE urakka = " urakka-id ";"))]
    (is (integer? yhatiedot-ennen-testia) "Urakka on jo sidottu ennen testiä")

    (u "UPDATE yhatiedot SET sidonta_lukittu = TRUE WHERE urakka = " urakka-id ";")
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :sido-yha-urakka-harja-urakkaan +kayttaja-jvh+
                                                   {:harja-urakka-id urakka-id
                                                    :yha-tiedot {:yhatunnus "YHATUNNUS"
                                                                 :yhaid 666
                                                                 :yhanimi "YHANIMI"}})))))

(deftest ala-sido-vajailla-tiedoilla
  (let [urakka-id (hae-urakan-id-nimella "YHA-päällystysurakka")
        yhatiedot-ennen-testia (ffirst (q "SELECT id FROM yhatiedot WHERE urakka = " urakka-id ";"))]
    (is (nil? yhatiedot-ennen-testia) "Urakan yhatiedot on tyhjä ennen testiä")

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :sido-yha-urakka-harja-urakkaan +kayttaja-jvh+
                                           {:harja-urakka-id urakka-id
                                            :yha-tiedot {}})))))

(deftest alla-anna-sitoa-ilman-oikeuksia
  (let [urakka-id (hae-urakan-id-nimella "YHA-päällystysurakka")]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :sido-yha-urakka-harja-urakkaan +kayttaja-ulle+
                                           {:harja-urakka-id urakka-id
                                            :yha-tiedot {:yhatunnus "YHATUNNUS"
                                                         :yhaid 666
                                                         :yhanimi "YHANIMI"}})))))

(deftest hae-yha-urakat
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")]

    (with-fake-http [urakoiden-haku-test/urakkahaku-url +onnistunut-urakoiden-hakuvastaus+]
      (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-urakat-yhasta +kayttaja-jvh+
                                    {:urakka-id urakka-id})]
        (is (= vastaus
               [{:elyt ["POP"]
                 :vuodet [2016]
                 :yhatunnus "YHATUNNUS"
                 :sampotunnus "SAMPOTUNNUS"
                 :yhaid 3}]))))))

(defn- luo-yha-kohteet [kohteen-osoite alikohteen-osoite]
  [{:alikohteet [{:yha-id 1
                  :tierekisteriosoitevali (merge {:karttapaivamaara #inst "2017-01-01T22:00:00.000-00:00"}
                                                 alikohteen-osoite)
                  :tunnus nil
                  :paallystystoimenpide {:kokonaismassamaara 10
                                         :paallystetyomenetelma 21
                                         :kuulamylly nil
                                         :raekoko 16
                                         :rc-prosentti nil
                                         :uusi-paallyste 14}
                  :yllapitoluokka 8
                  :keskimaarainen-vuorokausiliikenne 5000
                  :nykyinen-paallyste 14}]
    :yha-id 2
    :tierekisteriosoitevali (merge {:karttapaivamaara #inst "2017-01-01T22:00:00.000-00:00"}
                                   kohteen-osoite)
    :yha-kohdenumero 1
    :yllapitokohdetyyppi "paallyste"
    :nimi "YHA-kohde"
    :yllapitokohdetyotyyppi :paallystys}])


(deftest paattele-yhaan-lahetettava-sampoid-onnistuu
  "Palveluurakasta lähetetään palvelusopimuksen sampoid, kokonaisurakasta lähetetään urakan sampoid."
  (let [muhos-kokonaisurakka-urakkaid (ffirst (q "select id from urakka where sampoid = '4242523-TES2'"))
        oulu-palvelusopimus-urakkaid (ffirst (q "select id from urakka where sampoid = '666343-TES6'"))
        muhos-urakka (first (yha-kyselyt/hae-urakan-yhatiedot (:db jarjestelma) {:urakka muhos-kokonaisurakka-urakkaid}))
        oulu-urakka (first (yha-kyselyt/hae-urakan-yhatiedot (:db jarjestelma) {:urakka oulu-palvelusopimus-urakkaid}))]
    (is (= "4242523-TES2" (yha-integraatio/yhaan-lahetettava-sampoid muhos-urakka)))
    (is (= "TYP-3003" (yha-integraatio/yhaan-lahetettava-sampoid oulu-urakka)))))
