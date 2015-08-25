(ns harja.palvelin.integraatiot.api.havainnon-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.havainnot :as api-havainnot]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [cheshire.core :as cheshire]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet) [:db])
    :api-havainnot (component/using
                     (api-havainnot/->Havainnot)
                     [:http-palvelin :db :liitteiden-hallinta :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-havainto
  (let [havainnot-kannassa-ennen-pyyntoa (ffirst (q (str "SELECT COUNT(*) FROM havainto;")))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/havainto"] kayttaja portti
                                         (-> "test/resurssit/api/havainto.json" slurp))]
    (cheshire/decode (:body vastaus) true)

    (is (= 200 (:status vastaus)))

    (let [havainnot-kannassa-pyynnon-jalkeen (ffirst (q (str "SELECT COUNT(*) FROM havainto;")))
          liite-id (ffirst (q (str "SELECT id FROM liite WHERE nimi = 'testihavainto36934853.jpg';")))
          havainto-id (ffirst (q (str "SELECT id FROM havainto WHERE kohde = 'testikohde36934853';")))
          kommentti-id (ffirst (q (str "SELECT id FROM kommentti WHERE kommentti = 'Testikommentti323353435';")))]
      (log/debug "liite-id: " liite-id)
      (log/debug "havainto-id: " havainto-id)
      (log/debug "kommentti-id: " kommentti-id)

      (is (= (+ havainnot-kannassa-ennen-pyyntoa 1) havainnot-kannassa-pyynnon-jalkeen))
      (is (number? liite-id))
      (is (number? havainto-id))
      (is (number? kommentti-id))

      (u (str "DELETE FROM havainto_liite WHERE havainto = " havainto-id ";"))
      (u (str "DELETE FROM havainto_kommentti WHERE havainto = " havainto-id ";"))
      (u (str "DELETE FROM kommentti WHERE kommentti = 'Testikommentti323353435';"))
      (u (str "DELETE FROM liite WHERE id = " liite-id ";"))
      (u (str "DELETE FROM havainto WHERE kuvaus = 'testihavainto36934853';")))))
