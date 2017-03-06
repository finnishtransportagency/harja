(ns harja.palvelin.integraatiot.api.pistetoteuman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.pistetoteuma :as api-pistetoteuma]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]))

(def kayttaja "destia")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-pistetoteuma (component/using
                        (api-pistetoteuma/->Pistetoteuma)
                        [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-kokonaishintainen-pistetoteuma
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
       vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja portti
                                               (-> "test/resurssit/api/pistetoteuma_yksittainen.json"
                                                   slurp
                                                   (.replace "__ID__" (str ulkoinen-id))
                                                   (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                                                   (.replace "__TOTEUMA_TYYPPI__" "kokonaishintainen")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy" "kokonaishintainen"]))
      (is (= (count toteuma-tehtava-idt) 1))

      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [vastaus-paivitys (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja portti
                                                      (-> "test/resurssit/api/pistetoteuma_yksittainen.json"
                                                          slurp
                                                          (.replace "__ID__" (str ulkoinen-id))
                                                          (.replace "__SUORITTAJA_NIMI__" "Peltikoneen Pojat Oy")
                                                          (.replace "__TOTEUMA_TYYPPI__" "kokonaishintainen")))]
        (is (= 200 (:status vastaus-paivitys)))
        (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))]
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Peltikoneen Pojat Oy" "kokonaishintainen"]))
          (is (= (count toteuma-tehtava-idt) 1)))

        (u (str "DELETE FROM reittipiste WHERE toteuma = " toteuma-id))
        (u (str "DELETE FROM toteuma_tehtava WHERE toteuma = " toteuma-id))
        (u (str "DELETE FROM toteuma WHERE ulkoinen_id = " ulkoinen-id))))))

(deftest tallenna-usea-pistetoteuma
  (let [ulkoiset-idt (tyokalut/hae-usea-vapaa-toteuma-ulkoinen-id 2)
        ulkoinen-id-1 (first ulkoiset-idt)
        ulkoinen-id-2 (second ulkoiset-idt)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja portti
                                                (-> "test/resurssit/api/pistetoteuma_monta.json"
                                                    slurp
                                                    (.replace "__ID1__" (str ulkoinen-id-1))
                                                    (.replace "__SUORITTAJA1_NIMI__" "Tienpesijät Oy")
                                                    (.replace "__TOTEUMA1_TYYPPI__" "kokonaishintainen")
                                                    (.replace "__ID2__" (str ulkoinen-id-2))
                                                    (.replace "__SUORITTAJA2_NIMI__" "Tienraivaajat Ry")
                                                    (.replace "__TOTEUMA2_TYYPPI__" "kokonaishintainen")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma1-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-1)))
          toteuma1-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-1)))
          toteuma1-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma1-id))))]
      (is (= toteuma1-kannassa [ulkoinen-id-1 "8765432-1" "Tienpesijät Oy" "kokonaishintainen"]))
      (is (= (count toteuma1-tehtava-idt) 1)))
    (let [toteuma2-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-2)))
          toteuma2-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-2)))
          toteuma2-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma2-id))))]
      (is (= toteuma2-kannassa [ulkoinen-id-2 "8765432-1" "Tienraivaajat Ry" "kokonaishintainen"]))
      (is (= (count toteuma2-tehtava-idt) 1)))))