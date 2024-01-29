(ns harja.palvelin.integraatiot.api.analytiikka-paallystyskohteet-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
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

(use-fixtures :each jarjestelma-fixture)

(deftest paallystyskohteiden-haku-esimerkki-validoituu
  (let [paallystyskohteet-esimerkki (slurp "resources/api/examples/analytiikka-paallystyskohteiden-haku-response.json")
        aikataulut-esimerkki (slurp "resources/api/examples/analytiikka-paallystyskohteiden-aikataulujen-haku-response.json")
        urakat-esimerkki (slurp "resources/api/examples/analytiikka-paallystysurakoiden-haku-response.json")
        ]
    (is (nil? (json/validoi json-skeemat/+analytiikka-paallystyskohteiden-haku-vastaus+ paallystyskohteet-esimerkki false)))
    (is (nil? (json/validoi json-skeemat/+analytiikka-paallystyskohteiden-aikataulujen-haku-vastaus+ aikataulut-esimerkki false)))
    (is (nil? (json/validoi json-skeemat/+analytiikka-paallystysurakoiden-haku-vastaus+ urakat-esimerkki false)))))
