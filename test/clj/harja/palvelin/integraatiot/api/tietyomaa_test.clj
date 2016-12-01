(ns harja.palvelin.integraatiot.api.tietyomaa-test
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

(defn hae-tietyomaa []
  (first (q (str
              "SELECT id, muokattu, poistettu FROM tietyomaa
               WHERE osuus_id = 1 AND jarjestelma = 'Urakoitsijan järjestelmä' AND yllapitokohde = 5;"))))

(deftest tarkista-tietyomaan-kasittely
  (let [lisays-kutsu (slurp "test/resurssit/api/tietyomaan-kirjaus.json")
        poisto-kutsu (slurp "test/resurssit/api/tietyomaan-poisto.json")]
    (api-tyokalut/post-kutsu ["/api/urakat/5/yllapitokohteet/5/tietyomaa"] kayttaja portti lisays-kutsu)
    (let [uusi-tietyomaa (hae-tietyomaa)
          id (first uusi-tietyomaa)
          muokattu (second uusi-tietyomaa)]
      (is (not (nil? id)) "Kannasta löytyy uusi suljettu tieosuus")
      (is (nil? muokattu)) "Muokkauspäivämäärä on tyhjä")

    (api-tyokalut/post-kutsu ["/api/urakat/5/yllapitokohteet/5/tietyomaa"] kayttaja portti lisays-kutsu)
    (let [muokattu-tietyomaa (hae-tietyomaa)
          id (first muokattu-tietyomaa)
          muokattu (second muokattu-tietyomaa)]
      (is (not (nil? id)) "Kannasta löytyy yha sama suljettu tieosuus")
      (is (not (nil? muokattu))) "Tieosuus on merkitty muuttuneeksi")

    (api-tyokalut/delete-kutsu ["/api/urakat/5/yllapitokohteet/5/tietyomaa"] kayttaja portti poisto-kutsu)
    (let [muokattu-tietyomaa (hae-tietyomaa)
          id (first muokattu-tietyomaa)
          muokattu (nth muokattu-tietyomaa 2)]
      (is (not (nil? id)) "Kannasta löytyy yha sama suljettu tieosuus")
      (is (not (nil? muokattu))) "Tieosuus on merkitty poistetuksi")))


