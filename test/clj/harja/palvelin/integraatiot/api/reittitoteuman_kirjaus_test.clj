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


(deftest ^:perf yksittainen-kirjaus-ei-kesta-liian-kauan
  (let [sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)]
    (is (apply
         gatling-onnistuu-ajassa?
         "Yksittäinen reittitoteuma"
         {:timeout-in-ms 3000}
         (take
           10
           (map
             (fn [ulkoinen-id]
               #(api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                        (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                            slurp
                                            (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                            (.replace "__ID__" (str ulkoinen-id))
                                            (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy"))))
             (range)))))))

(deftest tallenna-yksittainen-reittitoteuma
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                    slurp
                                                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy"]))


      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [vastaus-paivitys (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                      (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                          slurp
                                                          (.replace "__SOPIMUS_ID__" (str sopimus-id))
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

; Kommentoitu 27.3.2020 reittitoteumaongelman takia väliaikaisesti
;(deftest tallenna-yksittainen-reittitoteuma-ilman-sopimusta-paivittaa-cachen
;  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
;        sopimus-id (ffirst (q (str "SELECT id FROM sopimus WHERE urakka = " 2 " AND paasopimus IS NULL")))
;        sopimuksen_kaytetty_materiaali-maara-ennen (ffirst (q (str "SELECT count(*) FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id)))
;        kaytetty-talvisuolaliuos-odotettu 4.62M
;        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
;                                                (-> "test/resurssit/api/reittitoteuma_yksittainen_ilman_sopimusta.json"
;                                                    slurp
;                                                    (.replace "__ID__" (str ulkoinen-id))
;                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
;    (is (= 200 (:status vastaus-lisays)))
;    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
;          sopimuksen_kaytetty_materiaali-jalkeen (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id))]
;       (println "sop käyt jälkeen " sopimuksen_kaytetty_materiaali-jalkeen)
;      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy"]))
;       (is (= 0 sopimuksen_kaytetty_materiaali-maara-ennen))
;       (is (= 1 (count sopimuksen_kaytetty_materiaali-jalkeen)))
;       (is (= kaytetty-talvisuolaliuos-odotettu (last (first sopimuksen_kaytetty_materiaali-jalkeen)))))))


(deftest tallenna-usea-reittitoteuma
  (let [ulkoiset-idt (tyokalut/hae-usea-vapaa-toteuma-ulkoinen-id 2)
        ulkoinen-id-1 (first ulkoiset-idt)
        ulkoinen-id-2 (second ulkoiset-idt)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                (-> "test/resurssit/api/reittitoteuma_monta.json"
                                                    slurp
                                                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
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
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                    slurp
                                                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuman-sopimus-id (ffirst (q (str "SELECT sopimus FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= sopimus-id toteuman-sopimus-id) "Toteuma kirjattiin pääsopimukselle")
      (poista-reittitoteuma toteuma-id ulkoinen-id))))

(deftest tarkista-toteuman-tallentaminen-ilman-oikeuksia
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] "LX123456789" portti
                                                (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 403 (:status vastaus-lisays)))))

(deftest tarkista-toteuman-tallentaminen-lisaoikeudella
  (u "INSERT INTO kayttajan_lisaoikeudet_urakkaan (urakka, kayttaja) VALUES (" urakka ", "
     (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'destia';")) ");")
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] "destia" portti
                                                (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                    slurp
                                                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (u "DELETE FROM kayttajan_lisaoikeudet_urakkaan;")
    (is (= 200 (:status vastaus-lisays)))))

(defn laheta-yksittainen-reittitoteuma []
  (let [ulkoinen-id (str (tyokalut/hae-vapaa-toteuma-ulkoinen-id))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        vastaus (api-tyokalut/post-kutsu
                 ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                 (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                     slurp
                     (.replace "__SOPIMUS_ID__" (str sopimus-id))
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

; Kommentoitu 27.3.2020 reittitoteumaongelmien ajaksi
;(deftest materiaalin-kaytto-paivittyy-oikein
;  (let [poistetaan-aluksi-materiaalit-cachesta (u "DELETE FROM urakan_materiaalin_kaytto_hoitoluokittain")
;        hae-materiaalit #(q "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain")
;        materiaalin-kaytto-ennen (hae-materiaalit)]
;    (testing "Materiaalin käyttö on tyhjä aluksi"
;      (is (empty? materiaalin-kaytto-ennen)))
;
;    (testing "Uuden materiaalitoteuman lähetys lisää päivälle rivin"
;      (let [ulkoinen-id  (laheta-yksittainen-reittitoteuma)]
;        (let [rivit1 (hae-materiaalit)
;              maara1 (-> rivit1 first last)]
;          (is (= 1 (count rivit1)))
;          (is (=marginaalissa? maara1 4.62) "Suolaa 4.62")
;
;          (testing "Uusi toteuma samalle päivälle, kasvattaa lukua"
;            ;; Lähetetään uusi toteuma, määrän pitää tuplautua ja rivimäärä olla sama
;            (laheta-yksittainen-reittitoteuma)
;            (let [rivit2 (hae-materiaalit)
;                  maara2 (-> rivit2 first last)]
;              (is (= 1 (count rivit2)) "rivien määrä pysyy samana")
;              (is (=marginaalissa? maara2 (* 2 maara1)) "Määrä on tuplautunut")))
;
;          (testing "Ensimmäisen toteuman poistaminen vähentää määriä"
;            (poista-toteuma ulkoinen-id)
;
;            (let [rivit3 (hae-materiaalit)
;                  maara3 (-> rivit3 first last)]
;              (is (= 1 (count rivit3)) "Rivejä on sama määrä")
;              (is (=marginaalissa? maara3 4.62) "Määrä on laskenut takaisin"))))))))


(deftest lahetys-tuntemattomalle-urakalle-ei-toimi []
  (let [ulkoinen-id (str (tyokalut/hae-vapaa-toteuma-ulkoinen-id))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        vastaus (api-tyokalut/post-kutsu
                  ["/api/urakat/" 666 "/toteumat/reitti"] kayttaja portti
                  (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                      slurp
                      (.replace "__SOPIMUS_ID__" (str sopimus-id))
                      (.replace "__ID__" (str ulkoinen-id))
                      (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 400 (:status vastaus)) "Statuksena viallinen kutsu")
    (is (.contains (:body vastaus ) "Urakkaa id:llä 666 ei löydy"))))

(deftest eri-urakalle-samalla-kayttajalla-ja-ulkoisella-idlla-tallentaminen
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        oulun-alueurakka-id (hae-oulun-alueurakan-2014-2019-id)
        kajaanin-alueurakka-id (hae-kajaanin-alueurakan-2014-2019-id)
        oulun-sopimus-id (hae-annetun-urakan-paasopimuksen-id oulun-alueurakka-id)
        kajaanin-sopimus-id (hae-annetun-urakan-paasopimuksen-id kajaanin-alueurakka-id)
        kayttaja (ffirst (q (str "SELECT kayttajanimi
                                  FROM kayttaja
                                  WHERE organisaatio=(SELECT hallintayksikko FROM urakka WHERE id=" oulun-alueurakka-id ") AND "
                                 "organisaatio=(SELECT hallintayksikko FROM urakka WHERE id=" kajaanin-alueurakka-id ")")))
        ;; Annetaan käyttäjälle lisäoikeudet ja tehdään siitä järjestelmä, jotta api-kutsut menee läpi.
        _ (u "INSERT INTO kayttajan_lisaoikeudet_urakkaan (urakka, kayttaja) VALUES
        (" oulun-alueurakka-id ", " (ffirst (q (str "SELECT id FROM kayttaja WHERE kayttajanimi = '" kayttaja "'"))) "),
        (" kajaanin-alueurakka-id ", " (ffirst (q (str "SELECT id FROM kayttaja WHERE kayttajanimi = '" kayttaja "'"))) ")")
        _ (u "UPDATE kayttaja SET jarjestelma = TRUE WHERE kayttajanimi= '" kayttaja "'")

        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" oulun-alueurakka-id "/toteumat/reitti"] kayttaja portti
                                                (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                    slurp
                                                    (.replace "__SOPIMUS_ID__" (str oulun-sopimus-id))
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienharjaajat Oy")))
        _ (is (= 200 (:status vastaus-lisays)))
        toinen-vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" kajaanin-alueurakka-id "/toteumat/reitti"] kayttaja portti
                                                       (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                           slurp
                                                           (.replace "__SOPIMUS_ID__" (str kajaanin-sopimus-id))
                                                           (.replace "__ID__" (str ulkoinen-id))
                                                           (.replace "__SUORITTAJA_NIMI__" "Tienharjaajat Oy")))]

    (is (= 200 (:status toinen-vastaus-lisays)))))

(deftest paivita-reittitoteuma-monesti-hoitoluokittaiset-summat-paivitetaan-oikein
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        reittototeumakutsu-joka-tehdaan-monesti (fn [urakka kayttaja portti sopimus-id ulkoinen-id]
                                                  (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                                           (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                                               slurp
                                                                               (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                                                               (.replace "__ID__" (str ulkoinen-id))
                                                                               (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy"))))
        vastaus-lisays (reittototeumakutsu-joka-tehdaan-monesti urakka kayttaja portti sopimus-id ulkoinen-id)
        hoitoluokittaiset-eka-kutsun-jalkeen (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka))
        sopimuksen-mat-kaytto-eka-kutsun-jalkeen (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id))
        hoitoluokittaiset-toka-kutsun-jalkeen (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka))
        sopimuksen-mat-kaytto-toka-kutsun-jalkeen (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy"]))

      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [vastaus-paivitys (reittototeumakutsu-joka-tehdaan-monesti urakka kayttaja portti sopimus-id ulkoinen-id)
            hoitoluokittaiset-kolmannen-kutsun-jalkeen (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka))
            sopimuksen-mat-kaytto-kolmannen-kutsun-jalkeen (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id))]
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
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy"]))
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

          (poista-reittitoteuma toteuma-id ulkoinen-id))
        (is (= hoitoluokittaiset-eka-kutsun-jalkeen
               hoitoluokittaiset-toka-kutsun-jalkeen
               hoitoluokittaiset-kolmannen-kutsun-jalkeen))
        (is (= sopimuksen-mat-kaytto-eka-kutsun-jalkeen
               sopimuksen-mat-kaytto-toka-kutsun-jalkeen
               sopimuksen-mat-kaytto-kolmannen-kutsun-jalkeen))))))
