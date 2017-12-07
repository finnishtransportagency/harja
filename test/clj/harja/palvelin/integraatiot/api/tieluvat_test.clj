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

    (is (= {::tielupa/ely uudenmaan-elyn-id} (tieluvat/hae-ely db "Uusimaa" {})))
    (is (thrown+?
          #(tyokalut/tasmaa-poikkeus
             %
             virheet/+viallinen-kutsu+
             virheet/+tuntematon-ely+
             "Tuntematon ELY Tuntematon")
          (tieluvat/hae-ely db "Tuntematon" {})))))

(deftest hae-sijainnit
  (let [db (luo-testitietokanta)
        tielupa-pistesijainnilla {::tielupa/sijainnit [{:harja.domain.tielupa/tie 20
                                                        :harja.domain.tielupa/aet 1
                                                        :harja.domain.tielupa/aosa 1}]}
        tielupa-pistesijainteineen (tieluvat/hae-tieluvan-sijainnit db tielupa-pistesijainnilla)
        tielupa-sijaintivalilla {::tielupa/sijainnit [{:harja.domain.tielupa/tie 20
                                                       :harja.domain.tielupa/aet 1
                                                       :harja.domain.tielupa/aosa 1
                                                       :losa 1
                                                       :let 300}]}
        tielupa-sijaintivaleineen (tieluvat/hae-tieluvan-sijainnit db tielupa-sijaintivalilla)
        tarkasta-tielupa (fn [ilman-sijainti sijainnin-kanssa]
                           (let [avaimet (fn [tielupa] (mapv #(select-keys % [::tielupa/tie ::tielupa/aosa ::tielupa/aet])
                                                             (::tielupa/sijainnit tielupa)))]
                             (is (= (avaimet ilman-sijainti) (avaimet sijainnin-kanssa))))
                           (is (every? #(not (nil? (::tielupa/geometria %))) (::tielupa/sijainnit sijainnin-kanssa))))]
    (tarkasta-tielupa tielupa-pistesijainnilla tielupa-pistesijainteineen)
    (tarkasta-tielupa tielupa-sijaintivalilla tielupa-sijaintivaleineen)))

