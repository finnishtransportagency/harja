(ns harja.palvelin.integraatiot.api.tieluvat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.palvelin.integraatiot.api.tieluvat :as tieluvat]
            [harja.domain.tielupa :as tielupa]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [slingshot.slingshot :refer [throw+]]
            [slingshot.test]))

(def kayttaja "livi")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet nil) [:db])
    :api-turvallisuuspoikkeama (component/using (tieluvat/->Tieluvat)
                                                [:http-palvelin :db :integraatioloki :liitteiden-hallinta])))

(use-fixtures :once jarjestelma-fixture)

(deftest hae-ely
  (let [db (luo-testitietokanta)
        uudenmaan-elyn-id (ffirst (q "select id from organisaatio where elynumero = 1;"))]

    (is (= {::tielupa/ely uudenmaan-elyn-id} (tieluvat/hae-ely db {} "Uusimaa")))
    (is (thrown+?
          #(tyokalut/tasmaa-poikkeus
             %
             virheet/+viallinen-kutsu+
             virheet/+tuntematon-ely+
             "Tuntematon ELY Tuntematon")
          (tieluvat/hae-ely db {} "Tuntematon")))))

