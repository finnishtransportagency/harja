(ns harja.palvelin.integraatiot.api.reittitoteuman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.reittitoteuma :as api-reittitoteuma]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [specql.core :refer [fetch columns]]
            [harja.domain.reittipiste :as rp]))

(def kayttaja "destia")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-reittitoteuma (component/using
                         (api-reittitoteuma/->Reittitoteuma)
                         [:http-palvelin :db :db-replica :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn poista-reittitoteuma [toteuma-id ulkoinen-id]
  (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma = " toteuma-id))
  (u (str "DELETE FROM toteuma_materiaali WHERE toteuma = " toteuma-id))
  (u (str "DELETE FROM toteuma_tehtava WHERE toteuma = " toteuma-id))
  (u (str "DELETE FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))

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
                                                      (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                          slurp
                                                          (.replace "__ID__" (str ulkoinen-id))
                                                          (.replace "__SUORITTAJA_NIMI__" "Peltikoneen Pojat Oy")))]
        (is (= 200 (:status vastaus-paivitys)))
        (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              {reittipisteet ::rp/reittipisteet} (first (fetch ds ::rp/toteuman-reittipisteet
                                                               (columns ::rp/toteuman-reittipisteet)
                                                               {::rp/toteuma-id toteuma-id}))
              toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))
              toteuma-materiaali-idt (into [] (flatten (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuma-id))))
              toteuman-materiaali (ffirst (q (str "SELECT nimi FROM toteuma_materiaali
                                                    JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
                                                    WHERE toteuma = " toteuma-id)))]
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Peltikoneen Pojat Oy"]))
          (is (= (count reittipisteet) 3))
          (is (= (count toteuma-tehtava-idt) 2))
          (is (= (count toteuma-materiaali-idt) 1))
          (is (= toteuman-materiaali "Talvisuolaliuos NaCl"))

          (doseq [reittipiste reittipisteet]
            (let [reitti-tehtava-idt (into [] (map ::rp/toimenpidekoodi) (::rp/tehtavat reittipiste))
                  reitti-materiaali-idt (into [] (map ::rp/materiaalikoodi) (::rp/materiaalit reittipiste))
                  reitti-hoitoluokka (::rp/soratiehoitoluokka reittipiste)]
              (is (= (count reitti-tehtava-idt) 2))
              (is (= (count reitti-materiaali-idt) 1))
              (is (= reitti-hoitoluokka 7)))) ; testidatassa on reittipisteen koordinaateille hoitoluokka

          (poista-reittitoteuma toteuma-id ulkoinen-id))))))


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

(deftest tarkista-toteuman-tallentaminen-paasopimukselle
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          sopimus-id (ffirst (q (str "SELECT sopimus FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= 5 sopimus-id) "Toteuma kirjattiin pääsopimukselle")
      (poista-reittitoteuma toteuma-id ulkoinen-id))))

(deftest tarkista-toteuman-tallentaminen-ilman-oikeuksia
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] (:kayttajanimi +kayttaja-tero+) portti
                                                (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 403 (:status vastaus-lisays)))))

(deftest tarkista-toteuman-tallentaminen-lisaoikeudella
  (u "INSERT INTO kayttajan_lisaoikeudet_urakkaan (urakka, kayttaja) VALUES (" urakka ", "
     (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'destia';")) ");")
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] "destia" portti
                                                (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (u "DELETE FROM kayttajan_lisaoikeudet_urakkaan;")
    (is (= 200 (:status vastaus-lisays)))))

(defn laheta-yksittainen-reittitoteuma []
  (let [ulkoinen-id (str (tyokalut/hae-vapaa-toteuma-ulkoinen-id))
        vastaus (api-tyokalut/post-kutsu
                 ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                 (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                     slurp
                     (.replace "__ID__" (str ulkoinen-id))
                     (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 200 (:status vastaus)) "Reittitoteuman tallennus onnistuu")
    ulkoinen-id))

(defn poista-toteuma [ulkoinen-id]
  (let [vastaus (api-tyokalut/delete-kutsu
                 ["/api/urakat/" urakka "/toteumat/reitti"]
                 kayttaja portti
                 (-> "test/resurssit/api/toteuman-poisto.json"
                     slurp
                     (.replace "__ID__" (str ulkoinen-id))
                     (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                     (.replace "__PVM__" (json-tyokalut/json-pvm (java.util.Date.)))))]
    (is (= 200 (:status vastaus)) "Toteuman poisto onnistuu")))

(deftest materiaalin-kaytto-paivittyy-oikein
  (let [hae-materiaalit #(q "SELECT * FROM urakan_materiaalin_kaytto_hoitoluokittain")
        materiaalin-kaytto-ennen (hae-materiaalit)]
    (testing "Materiaalin käyttö on tyhjä aluksi"
      (is (empty? materiaalin-kaytto-ennen)))

    (testing "Uuden materiaalitoteuman lähetys lisää päivälle rivin"
      (let [ulkoinen-id  (laheta-yksittainen-reittitoteuma)]
        (let [rivit1 (hae-materiaalit)
              maara1 (-> rivit1 first last)]
          (is (= 1 (count rivit1)))
          (is (=marginaalissa? maara1 4.62) "Suolaa 4.62")

          (testing "Uusi toteuma samalle päivälle, kasvattaa lukua"
            ;; Lähetetään uusi toteuma, määrän pitää tuplautua ja rivimäärä olla sama
            (laheta-yksittainen-reittitoteuma)
            (let [rivit2 (hae-materiaalit)
                  maara2 (-> rivit2 first last)]
              (is (= 1 (count rivit2)) "rivien määrä pysyy samana")
              (is (=marginaalissa? maara2 (* 2 maara1)) "Määrä on tuplautunut")))

          (testing "Ensimmäisen toteuman poistaminen vähentää määriä"
            (poista-toteuma ulkoinen-id)

            (let [rivit3 (hae-materiaalit)
                  maara3 (-> rivit3 first last)]
              (is (= 1 (count rivit3)) "Rivejä on sama määrä")
              (is (=marginaalissa? maara3 4.62) "Määrä on laskenut takaisin"))))))))
