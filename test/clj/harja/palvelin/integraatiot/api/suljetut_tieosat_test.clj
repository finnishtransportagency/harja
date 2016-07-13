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

(defn hae-suljettu-tieosuus []
  (first (q (str
              "SELECT id, muokattu, poistettu FROM suljettu_tieosuus
               WHERE osuus_id = 1 AND jarjestelma = 'Urakoitsijan järjestelmä' AND yllapitokohde = 5;"))))

(deftest tarkista-suljetun-tieosan-kasittely
  (let [lisays-kutsu (slurp "test/resurssit/api/suljetun-tieosuuden-kirjaus.json")
        poisto-kutsu (slurp "test/resurssit/api/suljetun-tieosuuden-poisto.json")]
    (api-tyokalut/post-kutsu ["/api/urakat/5/yllapitokohteet/5/suljettu-tieosuus"] kayttaja portti lisays-kutsu)
    (let [uusi-suljettu-tieosuus (hae-suljettu-tieosuus)
          id (first uusi-suljettu-tieosuus)
          muokattu (second uusi-suljettu-tieosuus)]
      (is (not (nil? id)) "Kannasta löytyy uusi suljettu tieosuus")
      (is (nil? muokattu)) "Muokkauspäivämäärä on tyhjä")

    (api-tyokalut/post-kutsu ["/api/urakat/5/yllapitokohteet/5/suljettu-tieosuus"] kayttaja portti lisays-kutsu)
    (let [muokattu-suljettu-tieosuus (hae-suljettu-tieosuus)
          id (first muokattu-suljettu-tieosuus)
          muokattu (second muokattu-suljettu-tieosuus)]
      (is (not (nil? id)) "Kannasta löytyy yha sama suljettu tieosuus")
      (is (not (nil? muokattu))) "Tieosuus on merkitty muuttuneeksi")

    (api-tyokalut/delete-kutsu ["/api/urakat/5/yllapitokohteet/5/suljettu-tieosuus"] kayttaja portti poisto-kutsu)
    (let [muokattu-suljettu-tieosuus (hae-suljettu-tieosuus)
          id (first muokattu-suljettu-tieosuus)
          muokattu (nth muokattu-suljettu-tieosuus 2)]
      (is (not (nil? id)) "Kannasta löytyy yha sama suljettu tieosuus")
      (is (not (nil? muokattu))) "Tieosuus on merkitty poistetuksi")))


