(ns harja.palvelin.integraatiot.api.tietyomaa-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.yllapitokohteet :as api-yllapitokohteet]))

(def kayttaja "skanska")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-yllapitokohteet (component/using
                           (api-yllapitokohteet/->Yllapitokohteet)
                           [:http-palvelin :db :integraatioloki :liitteiden-hallinta])))

(use-fixtures :once jarjestelma-fixture)

(defn hae-tietyomaa []
  (first (q (str
              "SELECT id, muokattu, poistettu, nopeusrajoitus FROM tietyomaa
               WHERE osuus_id = 1 AND jarjestelma = 'Urakoitsijan järjestelmä' AND yllapitokohde = 5;"))))

(deftest tarkista-tietyomaan-kasittely
  (let [lisays-kutsu (slurp "test/resurssit/api/tietyomaan-kirjaus.json")
        poisto-kutsu (slurp "test/resurssit/api/tietyomaan-poisto.json")]
    (api-tyokalut/post-kutsu ["/api/urakat/5/yllapitokohteet/5/tietyomaa"] kayttaja portti lisays-kutsu)
    (let [[id muokattu poistettu nopeusrajoitus] (hae-tietyomaa)]
      (is (not (nil? id)) "Kannasta löytyy uusi tietyömaa")
      (is (nil? muokattu) "Muokkauspäivämäärä on tyhjä")
      (is (nil? poistettu) "Tietyomaata ei ole poistettu")
      (is (= 20 nopeusrajoitus) "Nopeusrajoitus on kirjattu oikein"))

    (api-tyokalut/post-kutsu ["/api/urakat/5/yllapitokohteet/5/tietyomaa"] kayttaja portti lisays-kutsu)
    (let [[id muokattu _ _] (hae-tietyomaa)]
      (is (not (nil? id)) "Kannasta löytyy yha sama tietyömaa")
      (is (not (nil? muokattu)) "Tieosuus on merkitty muuttuneeksi"))

    (api-tyokalut/delete-kutsu ["/api/urakat/5/yllapitokohteet/5/tietyomaa"] kayttaja portti poisto-kutsu)
    (let [[id _ poistettu _] (hae-tietyomaa)]
      (is (not (nil? id)) "Kannasta löytyy yha sama tietyömaa")
      (is (not (nil? poistettu))) "Tieosuus on merkitty poistetuksi")))


