(ns harja.palvelin.integraatiot.api.laatupoikkeaman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [clojure.data :refer [diff]]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.laatupoikkeamat :as api-laatupoikkeamat]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [cheshire.core :as cheshire]))

(def kayttaja "yit-rakennus")
(def kayttaja-jvh "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet nil nil nil) [:db])
    :api-laatupoikkeamat (component/using
                           (api-laatupoikkeamat/->Laatupoikkeamat)
                           [:http-palvelin :db :liitteiden-hallinta :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest api-tallenna-laatupoikkeama-hoito-urakalle-toimii
  (let [laatupoikkeamat-kannassa-ennen-pyyntoa (ffirst (q (str "SELECT COUNT(*) FROM laatupoikkeama;")))
        liitteiden-maara-ennen (first (first (q "select count(id) FROM liite")))
        _ (anna-kirjoitusoikeus kayttaja)
        _ (anna-kirjoitusoikeus kayttaja-jvh)
        urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2005-2012")
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/laatupoikkeama"] kayttaja portti
                                         (-> "test/resurssit/api/laatupoikkeama.json" slurp))
        vastaus2 (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/laatupoikkeama"] kayttaja-jvh portti
                  (-> "test/resurssit/api/laatupoikkeama.json" slurp))
        liitteiden-maara-jalkeen (first (first (q "select count(id) FROM liite")))]
    (is (contains? (cheshire/decode (:body vastaus) true) :ilmoitukset))

    (is (= 200 (:status vastaus)))
    (is (= 200 (:status vastaus2)))
    ;; Varmistetaan vielä, että mitään ei muuttunut. Laatupoikkeama päivittyi samalla id:llä kuin alkuperäinen
    ;; Vaikka käyttäjä onkin eri
    (is (= [nil nil "{\"ilmoitukset\":\"Laatupoikkeama kirjattu onnistuneesti\"}"] (diff (:body vastaus) (:body vastaus2))))

    ;; Vain yksi liite tallennetaan kantaan, vaikka samat tiedot tallennetaan kahdesti
    (is (+ 1 liitteiden-maara-ennen) liitteiden-maara-jalkeen)

    (let [laatupoikkeamat-kannassa-pyynnon-jalkeen (ffirst (q (str "SELECT COUNT(*) FROM laatupoikkeama;")))
          liite-id (ffirst (q (str "SELECT id FROM liite WHERE nimi = 'testihavainto36934853.jpg';")))
          laatupoikkeama-db (first (q-map (str "SELECT id, tr_numero FROM laatupoikkeama WHERE kohde = 'testikohde36934853';")))
          kommentti-id (ffirst (q (str "SELECT id FROM kommentti WHERE kommentti = 'Testikommentti323353435';")))]

      (is (= (+ laatupoikkeamat-kannassa-ennen-pyyntoa 1) laatupoikkeamat-kannassa-pyynnon-jalkeen))
      (is (number? liite-id))
      (is (number? (:id laatupoikkeama-db)))
      (is (number? (:tr_numero laatupoikkeama-db)))
      (is (number? kommentti-id))

      (u "DELETE FROM laatupoikkeama_kommentti WHERE laatupoikkeama = (SELECT id FROM laatupoikkeama WHERE kohde = 'testikohde36934853') ;")
      (u "DELETE FROM laatupoikkeama_liite WHERE laatupoikkeama = (SELECT id FROM laatupoikkeama WHERE kohde = 'testikohde36934853');")
      (u "DELETE FROM laatupoikkeama WHERE id = (SELECT id FROM laatupoikkeama WHERE kohde = 'testikohde36934853');"))))

(deftest api-tallenna-laatupoikkeama-yllapito-urakalle-toimii
  (let [laatupoikkeamat-kannassa-ennen-pyyntoa (ffirst (q (str "SELECT COUNT(*) FROM laatupoikkeama;")))
        _ (anna-kirjoitusoikeus kayttaja-jvh)
        urakka-id (hae-urakan-id-nimella "Tienpäällystysurakka KAS ELY 1 2015")
        ;; Haetaan yllapitokohde-id, jotta voidaan varmistaa, että laatupoikkeama tallentuu oikein yllapitokohteelle
        yllapitokohde (first (q-map (format "SELECT id, nimi FROM yllapitokohde WHERE urakka = %s", urakka-id)))
        yllapitokohde-nimi (:nimi yllapitokohde)
        yllapitokohde-id (:id yllapitokohde)
        laatupoikkeama-json (-> "test/resurssit/api/laatupoikkeama.json"
                              slurp
                              (.replace "testikohde36934853" yllapitokohde-nimi)
                              (.replace "123456" "654321"))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/laatupoikkeama"] kayttaja-jvh portti laatupoikkeama-json)]
    (is (contains? (cheshire/decode (:body vastaus) true) :ilmoitukset))

    (is (= 200 (:status vastaus)))

    (let [laatupoikkeamat-kannassa-pyynnon-jalkeen (ffirst (q (str "SELECT COUNT(*) FROM laatupoikkeama;")))
          laatupoikkeama-db (first (q-map (format "SELECT id, tr_numero, yllapitokohde FROM laatupoikkeama WHERE yllapitokohde = %s; " yllapitokohde-id)))
          kommentti-id (ffirst (q (str "SELECT id FROM kommentti WHERE kommentti = 'Testikommentti323353435';")))]

      (is (= (+ laatupoikkeamat-kannassa-ennen-pyyntoa 1) laatupoikkeamat-kannassa-pyynnon-jalkeen))
      (is (number? (:id laatupoikkeama-db)))
      (is (number? (:tr_numero laatupoikkeama-db)))
      (is (= yllapitokohde-id (:yllapitokohde laatupoikkeama-db)))
      (is (number? kommentti-id))

      (u "DELETE FROM laatupoikkeama_kommentti WHERE laatupoikkeama = (SELECT id FROM laatupoikkeama WHERE kohde = 'testikohde36934853') ;")
      (u "DELETE FROM laatupoikkeama_liite WHERE laatupoikkeama = (SELECT id FROM laatupoikkeama WHERE kohde = 'testikohde36934853');")
      (u "DELETE FROM laatupoikkeama WHERE id = (SELECT id FROM laatupoikkeama WHERE kohde = 'testikohde36934853');"))))
