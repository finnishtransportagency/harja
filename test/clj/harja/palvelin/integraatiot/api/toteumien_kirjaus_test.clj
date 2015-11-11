(ns harja.palvelin.integraatiot.api.toteumien-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.pistetoteuma :as api-pistetoteuma]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.reittitoteuma :as api-reittitoteuma]
            [harja.palvelin.integraatiot.api.varustetoteuma :as api-varustetoteuma]))

(def kayttaja "fastroi")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea kayttaja
                                           :api-pistetoteuma (component/using
                                                               (api-pistetoteuma/->Pistetoteuma)
                                                               [:http-palvelin :db :integraatioloki])
                                           :api-reittitoteuma (component/using
                                                                (api-reittitoteuma/->Reittitoteuma)
                                                                [:http-palvelin :db :integraatioloki])
                                           :api-varusteoteuma (component/using
                                                                (api-varustetoteuma/->Varustetoteuma)
                                                                [:http-palvelin :db :integraatioloki])
                                           ))

(use-fixtures :once jarjestelma-fixture)

(defn hae-vapaa-toteuma-ulkoinen-id []
  (let [id (rand-int 10000)
        vastaus (q (str "SELECT * FROM toteuma WHERE ulkoinen_id = '" id "';"))]
    (if (empty? vastaus) id (recur))))

(deftest tallenna-pistetoteuma
  (let [ulkoinen-id (hae-vapaa-toteuma-ulkoinen-id)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja portti
                                                (-> "test/resurssit/api/pistetoteuma.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                                                    (.replace "__TOTEUMA_TYYPPI__" "yksikkohintainen")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy" "yksikkohintainen"]))
      (is (= (count toteuma-tehtava-idt) 1))

      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [vastaus-paivitys (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja portti
                                                      (-> "test/resurssit/api/pistetoteuma.json"
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

(deftest tallenna-reittitoteuma
  (let [ulkoinen-id (hae-vapaa-toteuma-ulkoinen-id)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                (-> "test/resurssit/api/reittitoteuma.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy"]))

      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [vastaus-paivitys (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                      (-> "test/resurssit/api/reittitoteuma.json"
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
              (is (= reitti-hoitoluokka 7))))               ; testidatassa on reittipisteen koordinaateille hoitoluokka

          (doseq [reittipiste-id reittipiste-idt]
            (u (str "DELETE FROM reitti_materiaali WHERE reittipiste = " reittipiste-id))
            (u (str "DELETE FROM reitti_tehtava WHERE reittipiste = " reittipiste-id)))
          (u (str "DELETE FROM reittipiste WHERE toteuma = " toteuma-id))
          (u (str "DELETE FROM toteuma_materiaali WHERE toteuma = " toteuma-id))
          (u (str "DELETE FROM toteuma_tehtava WHERE toteuma = " toteuma-id))
          (u (str "DELETE FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))))))

(deftest tallenna-varustetoteuma
  (let [ulkoinen-id (hae-vapaa-toteuma-ulkoinen-id)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/varuste"] kayttaja portti
                                                (-> "test/resurssit/api/varustetoteuma.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          varuste-arvot-kannassa (first (q (str "SELECT arvot FROM varustetoteuma WHERE toteuma = " toteuma-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tehotekijät Oy"]))
      (is (= varuste-arvot-kannassa ["----livitunniste----        2                           ----livitunniste----          01  "])))))
