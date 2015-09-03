(ns harja.palvelin.integraatiot.api.turvallisuuspoikkeaman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.turvallisuuspoikkeama :as turvallisuuspoikkeama]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [cheshire.core :as cheshire]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet) [:db])
    :api-turvallisuuspoikkeama (component/using (turvallisuuspoikkeama/->Turvallisuuspoikkeama)
                                                [:http-palvelin :db])))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-turvallisuuspoikkeama
  (let [tp-kannassa-ennen-pyyntoa (ffirst (q (str "SELECT COUNT(*) FROM turvallisuuspoikkeama;")))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/"urakka"/poikkeamat/turvallisuuspoikkeamat"]
                                         kayttaja portti
                                         (-> "test/resurssit/api/turvallisuuspoikkeama.json" slurp))]
    (cheshire/decode (:body vastaus) true)

    (is (= 200 (:status vastaus)))

    (let [tp-kannassa-pyynnon-jalkeen (ffirst (q (str "SELECT COUNT(*) FROM turvallisuuspoikkeama;")))
          liite-id (ffirst (q (str "SELECT id FROM liite WHERE nimi = 'testitp36934853.jpg';")))
          tp-id (ffirst (q (str "SELECT id FROM turvallisuuspoikkeama WHERE kuvaus ='Aura-auto suistui tieltä väistäessä jalankulkijaa.'")))
          kommentti-id (ffirst (q (str "SELECT id FROM kommentti WHERE kommentti='Testikommentti';")))]

      (is (= (+ tp-kannassa-ennen-pyyntoa 1) tp-kannassa-pyynnon-jalkeen))
      (is (number? liite-id))
      (is (number? tp-id))
      (is (number? kommentti-id)))))
