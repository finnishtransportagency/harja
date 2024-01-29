(ns harja.palvelin.integraatiot.api.analytiikka-paikkauskohteet-test
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

(deftest paikkauskohteiden-haku-esimerkki-validoituu
  (let [esimerkki (slurp "resources/api/examples/analytiikka-paikkauskohteiden-haku-response.json")]
    (is (= nil (json/validoi json-skeemat/+analytiikka-paikkauskohteiden-haku-vastaus+ esimerkki false)))
    )
  )
