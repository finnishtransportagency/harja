(ns harja.palvelin.integraatiot.api.laatupoikkeaman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [clojure.data :refer [diff]]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.laatupoikkeamat :as api-laatupoikkeamat]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [cheshire.core :as cheshire]))

(def kayttaja "yit-rakennus")
(def kayttaja-jvh "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet nil) [:db])
    :api-laatupoikkeamat (component/using
                           (api-laatupoikkeamat/->Laatupoikkeamat)
                           [:http-palvelin :db :liitteiden-hallinta :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-laatupoikkeama
  (let [laatupoikkeamat-kannassa-ennen-pyyntoa (ffirst (q (str "SELECT COUNT(*) FROM laatupoikkeama;")))
        liitteiden-maara-ennen (first (first (q "select count(id) FROM liite")))
        _ (anna-kirjoitusoikeus kayttaja)
        _ (anna-kirjoitusoikeus kayttaja-jvh)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/laatupoikkeama"] kayttaja portti
                                         (-> "test/resurssit/api/laatupoikkeama.json" slurp))
        vastaus2 (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/laatupoikkeama"] kayttaja-jvh portti
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
          laatupoikkeama-id (ffirst (q (str "SELECT id FROM laatupoikkeama WHERE kohde = 'testikohde36934853';")))
          kommentti-id (ffirst (q (str "SELECT id FROM kommentti WHERE kommentti = 'Testikommentti323353435';")))]
      ;(log/debug "liite-id: " liite-id)
      ;(log/debug "laatupoikkeama-id: " laatupoikkeama-id)
      ;(log/debug "kommentti-id: " kommentti-id)

      (is (= (+ laatupoikkeamat-kannassa-ennen-pyyntoa 1) laatupoikkeamat-kannassa-pyynnon-jalkeen))
      (is (number? liite-id))
      (is (number? laatupoikkeama-id))
      (is (number? kommentti-id))

      (u "DELETE FROM laatupoikkeama_kommentti WHERE laatupoikkeama = (SELECT id FROM laatupoikkeama WHERE kohde = 'testikohde36934853') ;")
      (u "DELETE FROM laatupoikkeama_liite WHERE laatupoikkeama = (SELECT id FROM laatupoikkeama WHERE kohde = 'testikohde36934853');")
      (u "DELETE FROM laatupoikkeama WHERE id = (SELECT id FROM laatupoikkeama WHERE kohde = 'testikohde36934853');"))))
