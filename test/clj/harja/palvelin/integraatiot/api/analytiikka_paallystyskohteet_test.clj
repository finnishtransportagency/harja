(ns harja.palvelin.integraatiot.api.analytiikka-paallystyskohteet-test
  (:require [cheshire.core :as cheshire]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.tyokalut.json-validointi :as json]
            [harja.palvelin.integraatiot.api.analytiikka :as api-analytiikka]))

(def kayttaja-yit "yit-rakennus")
(def kayttaja-analytiikka "analytiikka-testeri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-yit
    :api-analytiikka (component/using
                       (api-analytiikka/->Analytiikka false)
                       [:http-palvelin :db-replica :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest paallystyskohteiden-haku-esimerkki-validoituu
  (let [paallystyskohteet-esimerkki (slurp "resources/api/examples/analytiikka-paallystyskohteiden-haku-response.json")
        aikataulut-esimerkki (slurp "resources/api/examples/analytiikka-paallystyskohteiden-aikataulujen-haku-response.json")
        urakat-esimerkki (slurp "resources/api/examples/analytiikka-paallystysurakoiden-haku-response.json")
        paallystysilmoitukset-pot2-esimerkki (slurp "resources/api/examples/analytiikka-paallystysilmoitusten-haku-response.json")
        hoidon-paikkaukset-esimerkki (slurp "resources/api/examples/analytiikka-hoidon-paikkauskustannusten-haku-response.json")]
    (is (nil? (json/validoi json-skeemat/+analytiikka-paallystyskohteiden-haku-vastaus+ paallystyskohteet-esimerkki false)))
    (is (nil? (json/validoi json-skeemat/+analytiikka-paallystyskohteiden-aikataulujen-haku-vastaus+ aikataulut-esimerkki false)))
    (is (nil? (json/validoi json-skeemat/+analytiikka-paallystysurakoiden-haku-vastaus+ urakat-esimerkki false)))
    (is (nil? (json/validoi json-skeemat/+analytiikka-paallystysilmoitusten-haku-vastaus+ paallystysilmoitukset-pot2-esimerkki false)))
    (is (nil? (json/validoi json-skeemat/+analytiikka-hoidon-paikkaukset-haku-vastaus+ hoidon-paikkaukset-esimerkki false)))))

(deftest paallystysurakoiden-haku-toimii
  (let [odotettu-vastaus (slurp "resources/api/examples/analytiikka-paallystysurakoiden-haku-response.json")
        alkuaika "2022-07-15T00:00:00Z"
        loppuaika "2022-07-15T23:59:59Z"
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/paallystysurakat/" alkuaika "/" loppuaika)]
                  "analytiikka-testeri" portti)]
    (is (=
          (cheshire/decode odotettu-vastaus)
          (cheshire/decode (:body vastaus))))))

(deftest paallystyskohteiden-haku-toimii
  (let [odotettu-vastaus (slurp "resources/api/examples/analytiikka-paallystyskohteiden-haku-response.json")
        alkuaika "2022-07-15T00:00:00Z"
        loppuaika "2022-07-15T23:59:59Z"
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/paallystyskohteet/" alkuaika "/" loppuaika)]
                  "analytiikka-testeri" portti)]
    (is (=
          (cheshire/decode odotettu-vastaus)
          (cheshire/decode (:body vastaus))))))

(deftest paallystyskohteiden-aikataulujen-haku-toimii
  (let [odotettu-vastaus (slurp "resources/api/examples/analytiikka-paallystyskohteiden-aikataulujen-haku-response.json")
        alkuaika "2022-07-15T00:00:00Z"
        loppuaika "2022-07-15T23:59:59Z"
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/paallystyskohteiden-aikataulut/" alkuaika "/" loppuaika)]
                  "analytiikka-testeri" portti)]
    (is (=
          (cheshire/decode odotettu-vastaus)
          (cheshire/decode (:body vastaus))))))

(deftest paallystysilmoitusten-haku-toimii
  (let [odotettu-vastaus (slurp "resources/api/examples/analytiikka-paallystysilmoitusten-haku-response.json")
        alkuaika "2023-12-15T00:00:00Z"
        loppuaika "2023-12-15T23:59:59Z"
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/paallystysilmoitukset/" alkuaika "/" loppuaika)]
                  "analytiikka-testeri" portti)]
    (is (=
          (cheshire/decode odotettu-vastaus)
          (cheshire/decode (:body vastaus))))))

(deftest hoidon-paikkauskustannukset-haku-toimii
  (let [odotettu-vastaus (slurp "resources/api/examples/analytiikka-hoidon-paikkauskustannusten-haku-response.json")
        alkuaika "2023-11-01T00:00:00Z"
        loppuaika "2023-11-01T23:59:59Z"
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/hoidon-paikkauskustannukset/" alkuaika "/" loppuaika)]
                  "analytiikka-testeri" portti)]
    (is (=
          (cheshire/decode odotettu-vastaus)
          (cheshire/decode (:body vastaus))))))

(deftest paikkauskohteiden-haku-toimii
  (let [odotettu-vastaus (slurp "resources/api/examples/analytiikka-paikkauskohteiden-haku-response.json")
        alkuaika "2023-11-02T00:00:00Z"
        loppuaika "2023-11-02T23:59:59Z"
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/paikkauskohteet/" alkuaika "/" loppuaika)]
                  "analytiikka-testeri" portti)]
    (is (=
          (cheshire/decode odotettu-vastaus)
          (cheshire/decode (:body vastaus))))))

(deftest pot-paikkausten-haku-toimii
  (let [odotettu-vastaus (slurp "resources/api/examples/analytiikka-paikkausten-paallystysilmoitusten-haku-response.json")
        alkuaika "2023-11-02T00:00:00Z"
        loppuaika "2023-11-02T23:59:59Z"
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/paallystysilmoitukset/" alkuaika "/" loppuaika)]
                  "analytiikka-testeri" portti)]
    (is (=
          (cheshire/decode odotettu-vastaus)
          (cheshire/decode (:body vastaus))))))

(deftest paikkausten-haku-toimii
  (let [odotettu-vastaus (slurp "resources/api/examples/analytiikka-paikkausten-haku-response.json")
        alkuaika "2023-11-02T00:00:00Z"
        loppuaika "2023-11-02T23:59:59Z"
        vastaus (api-tyokalut/get-kutsu [(str "/api/analytiikka/paikkaukset/" alkuaika "/" loppuaika)]
                  "analytiikka-testeri" portti)]
    (is (=
          (cheshire/decode odotettu-vastaus)
          (cheshire/decode (:body vastaus))))))
