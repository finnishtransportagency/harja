(ns harja.palvelin.integraatiot.api.pistetoteuman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]
            [harja.palvelin.integraatiot.api.varustetoteuma :as api-varustetoteuma]))

(def kayttaja "fastroi")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-varusteoteuma (component/using
                         (api-varustetoteuma/->Varustetoteuma)
                         [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-varustetoteuma
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/varuste"] kayttaja portti
                                                (-> "test/resurssit/api/varustetoteuma.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi "
                                          "FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          varuste-arvot-kannassa (first (q (str "SELECT arvot FROM varustetoteuma WHERE toteuma = " toteuma-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tehotekijät Oy"]))
      (is (= varuste-arvot-kannassa ["----livitunniste----        2                           ----livitunniste----          01  "])))))