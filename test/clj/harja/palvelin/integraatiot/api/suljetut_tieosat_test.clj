(ns harja.palvelin.integraatiot.api.suljetut-tieosat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.yllapitokohteet :as api-yllapitokohteet]))

(def kayttaja "skanska")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
                                           :api-yllapitokohteet (component/using
                                                                  (api-yllapitokohteet/->Yllapitokohteet)
                                                                  [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tarkista-suljetun-tieosan-kirjaus
  (let [data (slurp "test/resurssit/api/suljetun-tieosuuden-kirjaus.json")]
    (api-tyokalut/post-kutsu ["/api/urakat/5/yllapitokohteet/5/suljettu-tieosuus"] kayttaja portti data)
    (let [uusi-suljettu-tieosuus  (q (str "SELECT id, muokattu FROM suljettu_tieosuus WHERE osuus_id = 1 AND jarjestelma = 'Urakoitsijan järjestelmä' AND yllapitokohde = 5;"))
          id (first uusi-suljettu-tieosuus)
          muokattu (second uusi-suljettu-tieosuus)]

      (is (not (nil? id)) "Kannasta löytyy uusi suljettu tieosuus")
      (is (nil? muokattu)) "Muokkauspäivämäärä on tyhjä")))


