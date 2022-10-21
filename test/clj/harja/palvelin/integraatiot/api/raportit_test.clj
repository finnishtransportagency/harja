(ns harja.palvelin.integraatiot.api.raportit-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.raportit :as api-raportit]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.kyselyt.konversio :as konversio]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
                                           :api-raportit
                                           (component/using
                                             (api-raportit/->Raportit)
                                             [:http-palvelin :db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest hae-urakan-materiaaliraportti
  (let [urakka-id 4
        alkupvm "2014-01-01"
        loppupvm "2019-12-31"
        vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" urakka-id "/raportit/materiaali/" alkupvm "/" loppupvm)]
                  kayttaja portti)
        encoodattu-body (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))))
