(ns harja.palvelin.integraatiot.api.yllapitokohteet_test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.yllapitokohteet :as api-yllapitokohteet]))

(def kayttaja "skanska")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-yllapitokohteet (component/using (api-yllapitokohteet/->Yllapitokohteet) [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tarkista-yllapitokohteiden-haku
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/5/yllapitokohteet"] kayttaja portti)
        data (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= 5 (count (:yllapitokohteet data))))))

(deftest yllapitokohteiden-haku-ei-toimi-ilman-oikeuksia
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/5/yllapitokohteet" urakka] "Erkki Esimerkki" portti)]
    (is (= 403 (:status vastaus)))
    (is (.contains (:body vastaus) "Tuntematon käyttäjätunnus: Erkki Esimerkki"))))

(deftest yllapitokohteiden-haku-ei-toimi-tuntemattomalle-urakalle
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/123467890/yllapitokohteet" urakka] kayttaja portti)]
    (is (= 400 (:status vastaus)))
    (is (.contains (:body vastaus) "tuntematon-urakka"))))

(deftest paallystysilmoituksen-kirjaaminen-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/paallystysilmoituksen_kirjaus.json"))]
    (log/debug "Vastaus: " (pr-str vastaus))
    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Päällystysilmoitus kirjattu onnistuneesti."))

    ;; TODO Tarkista arvot kannasta

    ))

(deftest aikataulun-kirjaaminen-ilmoituksettomalle-kohteelle-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/aikataulun_kirjaus.json"))]
    (log/debug "Vastaus: " (pr-str vastaus))
    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (.contains (:body vastaus) "Kohteella ei ole päällystysilmoitusta"))

    ;; TODO Tarkista arvot kannasta

    ))

(deftest aikataulun-kirjaaminen-toimii-kohteelle-jolla-ilmoitus
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-jolla-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/aikataulun_kirjaus.json"))]

    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (not (.contains (:body vastaus) "Kohteella ei ole päällystysilmoitusta")))

    ;; TODO Tarkista arvot kannasta

    ))

(deftest aikataulun-kirjaaminen-estaa-paivittamasta-urakkaan-kuulumatonta-kohdetta
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/aikataulun_kirjaus.json"))]
    (is (= 500 (:status vastaus)))))
