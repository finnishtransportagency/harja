(ns harja.palvelin.raportointi.cache-paivitys-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest toteuman-alkanut-muutos-paivittaa-urakan-materiaalinkayton-hoitoluokittain
  (let [urakka-id    (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        sopimus-id   (ffirst (q (str "SELECT id FROM sopimus WHERE urakka = " urakka-id " AND paasopimus IS NULL")))
        materiaali-id (ffirst (q "SELECT id FROM materiaalikoodi LIMIT 1"))
        vanha-pvm    "2015-01-10"
        uusi-pvm     "2015-01-20"
        lisatieto    "cache-paivitys-testi-toteuma"]

    ;; 1. Luodaan toteuma uudella päivämäärällä (alkanut = uusi-pvm, luotu = NOW() jotta muutospvm-haara löytää sen)  
    (u (str "INSERT INTO toteuma  
               (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi,  
                suorittajan_nimi, suorittajan_ytunnus, lisatieto)  
             VALUES ('harja-api'::lahde, " urakka-id ", " sopimus-id ",  
                     NOW(), '" uusi-pvm " 10:00:00'::timestamp, '" uusi-pvm " 10:30:00'::timestamp,  
                     'kokonaishintainen'::toteumatyyppi,  
                     'Testi Suorittaja', '1234567-8', '" lisatieto "')"))

    (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE lisatieto = '" lisatieto "'")))]

      ;; 2. Lisätään reittipisteet uudelle päivämäärälle (rp.aika = uusi-pvm)  
      ;;    Talvihoitoluokka = 3, materiaali = materiaali-id, maara = 5.0  
      (u (str "INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet)  
               VALUES (" toteuma-id ",  
                 ARRAY[  
                   ROW('" uusi-pvm " 10:05:00'::timestamp,  
                       st_makepoint(440816, 7198387)::POINT,  
                       3, NULL,  
                       ARRAY[]::reittipiste_tehtava[],  
                       ARRAY[ROW(" materiaali-id ", 5.0)::reittipiste_materiaali]::reittipiste_materiaali[]  
                   )::reittipistedata  
                 ]::reittipistedata[])"))

      ;; 3. Lisätään cache-rivi vanhalle päivämäärälle (simuloi tilannetta ennen alkanut-muutosta)  
      (u (str "INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain  
                 (pvm, materiaalikoodi, talvihoitoluokka, soratiehoitoluokka, urakka, maara, muokattu)  
               VALUES ('" vanha-pvm "'::date, " materiaali-id ", 3, 100, " urakka-id ", 100, NOW())  
               ON CONFLICT ON CONSTRAINT uniikki_urakan_materiaalin_kaytto_hoitoluokittain  
               DO UPDATE SET maara = 100"))

      ;; 4. Lisätään toteuma_muutos-rivi (vanha alkanut = vanha-pvm, ei vielä käsitelty)  
      (u (str "INSERT INTO toteuma_muutos  
                 (toteuma_id, urakka_id, vanha_alkanut, urakan_valimuisti_paivitetty)  
               VALUES (" toteuma-id ", " urakka-id ", '" vanha-pvm "'::timestamp, FALSE)"))

      ;; Varmistetaan lähtötilanne  
      (is (not (empty? (q-map (str "SELECT 1 FROM urakan_materiaalin_kaytto_hoitoluokittain  
                                    WHERE urakka = " urakka-id "  
                                      AND pvm = '" vanha-pvm "'::date"))))
        "Vanha päivämäärä on cachessa ennen funktiokutsua")

      (is (empty? (q-map (str "SELECT 1 FROM urakan_materiaalin_kaytto_hoitoluokittain  
                                WHERE urakka = " urakka-id "  
                                  AND pvm = '" uusi-pvm "'::date")))
        "Uusi päivämäärä ei ole vielä cachessa ennen funktiokutsua")

      ;; 5. Kutsutaan funktiota — muutospvm = tänään, jotta toteuma (luotu NOW()) löytyy muutospvm-haarasta  
      (q (str "SELECT paivita_urakan_materiaalikaytto_hoitoluokittain_muutospaivalla("
           urakka-id ", NOW()::date)"))

      ;; 6. Tarkistetaan: vanha päivämäärä on poistunut  
      (is (empty? (q-map (str "SELECT 1 FROM urakan_materiaalin_kaytto_hoitoluokittain  
                                WHERE urakka = " urakka-id "  
                                  AND pvm = '" vanha-pvm "'::date")))
        "Vanha päivämäärä on poistunut urakan_materiaalin_kaytto_hoitoluokittain-taulusta")

      ;; 7. Tarkistetaan: uusi päivämäärä löytyy cachesta (reittipisteiden kautta)  
      (is (not (empty? (q-map (str "SELECT 1 FROM urakan_materiaalin_kaytto_hoitoluokittain  
                                    WHERE urakka = " urakka-id "  
                                      AND pvm = '" uusi-pvm "'::date"))))
        "Uusi päivämäärä löytyy urakan_materiaalin_kaytto_hoitoluokittain-taulusta")

      ;; 8. Tarkistetaan materiaalin määrä — pitäisi olla reittipisteiden mukainen (5.0)  
      (is (= 5.0M (ffirst (q (str "SELECT maara FROM urakan_materiaalin_kaytto_hoitoluokittain  
                                   WHERE urakka = " urakka-id "  
                                     AND pvm = '" uusi-pvm "'::date  
                                     AND materiaalikoodi = " materiaali-id))))
        "Materiaalin määrä on oikea (reittipisteiden mukainen 5.0)")

      ;; 9. Tarkistetaan: toteuma_muutos-rivi on merkitty käsitellyksi
      (is (= true (ffirst (q (str "SELECT urakan_valimuisti_paivitetty  
                                   FROM toteuma_muutos  
                                   WHERE toteuma_id = " toteuma-id "  
                                     AND urakka_id = " urakka-id
                               " ORDER BY id DESC LIMIT 1"))))
        "toteuma_muutos-rivi on merkitty käsitellyksi")

      ;; Siivotaan  
      (u (str "DELETE FROM toteuma_muutos WHERE toteuma_id = " toteuma-id))
      (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma = " toteuma-id))
      (u (str "DELETE FROM toteuma WHERE id = " toteuma-id)))))

(deftest toteuman-alkanut-muutos-paivittaa-sopimuksen-materiaalinkayton
  (let [urakka-id     (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        sopimus-id    (ffirst (q (str "SELECT id FROM sopimus WHERE urakka = " urakka-id " AND paasopimus IS NULL")))
        materiaali-id (ffirst (q "SELECT id FROM materiaalikoodi LIMIT 1"))
        vanha-pvm     "2015-01-10"
        uusi-pvm      "2015-01-20"
        lisatieto     "cache-paivitys-testi-sopimus-toteuma"]

    ;; 1. Luodaan toteuma uudella päivämäärällä  
    (u (str "INSERT INTO toteuma  
               (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi,  
                suorittajan_nimi, suorittajan_ytunnus, lisatieto)  
             VALUES ('harja-ui'::lahde, " urakka-id ", " sopimus-id ",  
                     NOW(), '" uusi-pvm " 10:00:00'::timestamp, '" uusi-pvm " 10:30:00'::timestamp,  
                     'kokonaishintainen'::toteumatyyppi,  
                     'Testi Suorittaja', '1234567-8', '" lisatieto "')"))

    (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE lisatieto = '" lisatieto "'")))]

      ;; 2. Lisätään toteuma_materiaali-rivi uudelle päivämäärälle  
      ;;    (paivita_sopimuksen_materiaalin_kaytto laskee datan tästä taulusta)  
      (u (str "INSERT INTO toteuma_materiaali (toteuma, materiaalikoodi, maara, luotu, luoja)  
               VALUES (" toteuma-id ", " materiaali-id ", 42.0, NOW(), 1)"))

      ;; 3. Lisätään cache-rivi vanhalle päivämäärälle  
      (u (str "INSERT INTO sopimuksen_kaytetty_materiaali  
                 (sopimus, alkupvm, materiaalikoodi, maara, muokattu)  
               VALUES (" sopimus-id ", '" vanha-pvm "'::date, " materiaali-id ", 99.0, NOW())  
               ON CONFLICT ON CONSTRAINT uniikki_sop_pvm_mk  
               DO UPDATE SET maara = 99.0"))

      ;; 4. Lisätään toteuma_muutos-rivi (vanha alkanut = vanha-pvm, ei vielä käsitelty)  
      (u (str "INSERT INTO toteuma_muutos  
                 (toteuma_id, urakka_id, vanha_alkanut, sopimuksen_valimuisti_paivitetty)  
               VALUES (" toteuma-id ", " urakka-id ", '" vanha-pvm "'::timestamp, FALSE)"))

      ;; Varmistetaan lähtötilanne  
      (is (not (empty? (q-map (str "SELECT 1 FROM sopimuksen_kaytetty_materiaali  
                                    WHERE sopimus = " sopimus-id "  
                                      AND alkupvm = '" vanha-pvm "'::date"))))
        "Vanha päivämäärä on cachessa ennen funktiokutsua")

      (is (empty? (q-map (str "SELECT 1 FROM sopimuksen_kaytetty_materiaali  
                                WHERE sopimus = " sopimus-id "  
                                  AND alkupvm = '" uusi-pvm "'::date")))
        "Uusi päivämäärä ei ole vielä cachessa ennen funktiokutsua")

      ;; 5. Kutsutaan funktiota  
      (q (str "SELECT paivita_sopimuksen_materiaalikaytto_muutospaivalla("
           sopimus-id ", NOW()::date, " urakka-id ")"))

      ;; 6. Tarkistetaan: vanha päivämäärä on poistunut  
      (is (empty? (q-map (str "SELECT 1 FROM sopimuksen_kaytetty_materiaali  
                                WHERE sopimus = " sopimus-id "  
                                  AND alkupvm = '" vanha-pvm "'::date")))
        "Vanha päivämäärä on poistunut sopimuksen_kaytetty_materiaali-taulusta")

      ;; 7. Tarkistetaan: uusi päivämäärä löytyy cachesta (toteuma_materiaali-rivin kautta)  
      (is (not (empty? (q-map (str "SELECT 1 FROM sopimuksen_kaytetty_materiaali  
                                    WHERE sopimus = " sopimus-id "  
                                      AND alkupvm = '" uusi-pvm "'::date"))))
        "Uusi päivämäärä löytyy sopimuksen_kaytetty_materiaali-taulusta")

      ;; 8. Tarkistetaan materiaalin määrä — pitäisi olla toteuma_materiaali:n mukainen (42.0)  
      (is (= 42.0M (ffirst (q (str "SELECT maara FROM sopimuksen_kaytetty_materiaali  
                                    WHERE sopimus = " sopimus-id "  
                                      AND alkupvm = '" uusi-pvm "'::date  
                                      AND materiaalikoodi = " materiaali-id))))
        "Materiaalin määrä on oikea (toteuma_materiaali:n mukainen 42.0)")

      ;; 9. Tarkistetaan: toteuma_muutos-rivi on merkitty käsitellyksi
      (is (= true (ffirst (q (str "SELECT sopimuksen_valimuisti_paivitetty  
                                   FROM toteuma_muutos  
                                   WHERE toteuma_id = " toteuma-id "  
                                     AND urakka_id = " urakka-id
                               " ORDER BY id DESC LIMIT 1"))))
        "toteuma_muutos-rivi on merkitty käsitellyksi")

      ;; Siivotaan  
      (u (str "DELETE FROM toteuma_muutos WHERE toteuma_id = " toteuma-id))
      (u (str "DELETE FROM toteuma_materiaali WHERE toteuma = " toteuma-id))
      (u (str "DELETE FROM toteuma WHERE id = " toteuma-id)))))
