(ns harja.palvelin.integraatiot.api.reittitoteuman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.reittitoteuma :as api-reittitoteuma]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]))

(def kayttaja "fastroi")


(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-reittitoteuma (component/using
                         (api-reittitoteuma/->Reittitoteuma)
                         [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-yksittainen-reittitoteuma
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy"]))

      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [vastaus-paivitys (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                      (-> "test/resurssit/api/rreittitoteuma_yksittainen.json"
                                                          slurp
                                                          (.replace "__ID__" (str ulkoinen-id))
                                                          (.replace "__SUORITTAJA_NIMI__" "Peltikoneen Pojat Oy")))]
        (is (= 200 (:status vastaus-paivitys)))
        (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              reittipiste-idt (into [] (flatten (q (str "SELECT id FROM reittipiste WHERE toteuma = " toteuma-id))))
              toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))
              toteuma-materiaali-idt (into [] (flatten (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuma-id))))
              toteuman-materiaali (ffirst (q (str "SELECT nimi FROM toteuma_materiaali
                                                    JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
                                                    WHERE toteuma = " toteuma-id)))]
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Peltikoneen Pojat Oy"]))
          (is (= (count reittipiste-idt) 3))
          (is (= (count toteuma-tehtava-idt) 2))
          (is (= (count toteuma-materiaali-idt) 1))
          (is (= toteuman-materiaali "Talvisuolaliuos NaCl"))

          (doseq [reittipiste-id reittipiste-idt]
            (let [reitti-tehtava-idt (into [] (flatten (q (str "SELECT id FROM reitti_tehtava WHERE reittipiste = " reittipiste-id))))
                  reitti-materiaali-idt (into [] (flatten (q (str "SELECT id FROM reitti_materiaali WHERE reittipiste = " reittipiste-id))))
                  reitti-hoitoluokka (ffirst (q (str "SELECT soratiehoitoluokka FROM reittipiste WHERE id = " reittipiste-id)))]
              (is (= (count reitti-tehtava-idt) 2))
              (is (= (count reitti-materiaali-idt) 1))
              (is (= reitti-hoitoluokka 7)))) ; testidatassa on reittipisteen koordinaateille hoitoluokka

          (doseq [reittipiste-id reittipiste-idt]
            (u (str "DELETE FROM reitti_materiaali WHERE reittipiste = " reittipiste-id))
            (u (str "DELETE FROM reitti_tehtava WHERE reittipiste = " reittipiste-id)))
          (u (str "DELETE FROM reittipiste WHERE toteuma = " toteuma-id))
          (u (str "DELETE FROM toteuma_materiaali WHERE toteuma = " toteuma-id))
          (u (str "DELETE FROM toteuma_tehtava WHERE toteuma = " toteuma-id))
          (u (str "DELETE FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))))))


(deftest tallenna-usea-reittitoteuma
  (let [ulkoiset-idt (tyokalut/hae-usea-vapaa-toteuma-ulkoinen-id 2)
        ulkoinen-id-1 (first ulkoiset-idt)
        ulkoinen-id-2 (second ulkoiset-idt)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                (-> "test/resurssit/api/reittitoteuma_monta.json"
                                                    slurp
                                                    (.replace "__ID1__" (str ulkoinen-id-1))
                                                    (.replace "__SUORITTAJA1_NIMI__" "Tienpesijät Oy")
                                                    (.replace "__ID2__" (str ulkoinen-id-2))
                                                    (.replace "__SUORITTAJA2_NIMI__" "Tienraivaajat Oy")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma1-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-1)))]
      (is (= toteuma1-kannassa [ulkoinen-id-1 "8765432-1" "Tienpesijät Oy"])))
    (let [toteuma2-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-2)))]
      (is (= toteuma2-kannassa [ulkoinen-id-2 "8765432-1" "Tienraivaajat Oy"])))))