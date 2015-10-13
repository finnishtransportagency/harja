(ns harja.palvelin.integraatiot.api.siltatarkastuksien-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.reittitoteuma :as api-reittitoteuma]
            [harja.palvelin.integraatiot.api.varustetoteuma :as api-varustetoteuma]
            [harja.palvelin.integraatiot.api.siltatarkastukset :as api-siltatarkastukset]))

(def kayttaja "fastroi")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
                                           :api-siltatarkastukset (component/using
                                                               (api-siltatarkastukset/->Siltatarkastukset)
                                                               [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-pistetoteuma
  (let [ulkoinen-id 12345
        tarkastaja-etunimi "Martti"
        tarkastaja-sukunimi "Ahtisaari"
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/tarkastus/siltatarkastus"] kayttaja portti
                                                (-> "test/resurssit/api/siltatarkastus.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__ETUNIMI__" tarkastaja-etunimi)
                                                    (.replace "__SUKUNIMI__" tarkastaja-sukunimi)))]
    (is (= 200 (:status vastaus-lisays)))
    (let [siltatarkastus-kannassa (first (q (str "SELECT id, ulkoinen_id, tarkastaja, tarkastusaika FROM siltatarkastus WHERE ulkoinen_id = '" ulkoinen-id "'")))
          siltatarkastus-kannassa-id (first siltatarkastus-kannassa)]
    (is (not (nil? siltatarkastus-kannassa)))
    (is (= (nth siltatarkastus-kannassa 1) (str ulkoinen-id)))
    (is (= (nth siltatarkastus-kannassa 2) (str tarkastaja-etunimi " " tarkastaja-sukunimi)))

    (let [kohteet-kannassa (q (str "SELECT kohde, tulos, lisatieto FROM siltatarkastuskohde WHERE siltatarkastus = " siltatarkastus-kannassa-id))]
    (is (= (count kohteet-kannassa) 24))))))


#_(deftest paivita-siltatarkastus
  ; TODO
  )