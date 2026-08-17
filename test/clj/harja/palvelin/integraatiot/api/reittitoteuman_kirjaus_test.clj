(ns harja.palvelin.integraatiot.api.reittitoteuman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [harja.kyselyt.konversio :as konversio]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.reittitoteuma :as api-reittitoteuma]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [specql.core :refer [fetch columns]]
            [harja.domain.reittipiste :as rp]
            [clojure.data.json :as json]
            [cheshire.core :as cheshire])
  (:import (java.util Date)))

(def kayttaja "destia")
(def kayttaja-yit "yit-rakennus")
(def kayttaja-jvh "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-reittitoteuma (component/using
                         (api-reittitoteuma/->Reittitoteuma)
                         [:http-palvelin :db :db-replica :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn poista-reittitoteuma [toteuma-id ulkoinen-id urakka-id]
  (when toteuma-id
    (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma = " toteuma-id))
    (u (str "DELETE FROM toteuma_materiaali WHERE toteuma = " toteuma-id))
    (u (str "DELETE FROM toteuma_tehtava WHERE toteuma = " toteuma-id)))
  (when (and ulkoinen-id urakka-id)
    (u (str "DELETE FROM toteuma WHERE ulkoinen_id = " ulkoinen-id " AND urakka = " urakka-id))))

(deftest tallenna-epaonnistuva-viivageometria
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        y-tunnus (str (gensym))
        _ (anna-kirjoitusoikeus kayttaja)

        ;; Testataan virheellistä linestring kutsua, kyseinen json palauttaa 
        ;; MULTILINESTRING((548272.452 7053596.049), ...) jossa on vain 1 piste 
        kutsu (fn [ytunnus]
                (tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/reitti"] kayttaja portti
                  (-> "test/resurssit/api/reittitoteuma_epaonnistuva.json"
                    slurp
                    (.replace "__YTUNNUS__" ytunnus))))

        vastaus (kutsu y-tunnus)
        ;; Normaalisti kutsun pitäisi onnistua 
        _ (is (= 200 (:status vastaus)))
        toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE suorittajan_ytunnus = '" y-tunnus "'")))
        _ (is (= toteuma-kannassa [86123323 y-tunnus "Terranor Oy"]))

        ;; Muuta yhdista funktiota, jotta yksipisteinen multilinestring osuu ST_MakeLine, josta tulee SQL virhe 
        ;; ERROR: geometry requires more points
        _ (u "DROP FUNCTION yhdista_multilinestring(GEOMETRY);")
        _ (u "CREATE OR REPLACE FUNCTION yhdista_multilinestring(geometriat GEOMETRY)
                    RETURNS GEOMETRY AS $$
                    DECLARE
                      i INTEGER;
                      j INTEGER;
                      viiva GEOMETRY;
                      tulos GEOMETRY[];
                    BEGIN
                      tulos := ARRAY[]::GEOMETRY[];
                      FOR i IN 1..ST_NumGeometries(geometriat) LOOP
                        viiva := ST_GeometryN(geometriat, i);
                        CASE
                          WHEN ST_GeometryType(viiva) = 'ST_MultiLineString' THEN
                            FOR j IN 1..ST_NumGeometries(viiva) LOOP
                              tulos := tulos || ST_GeometryN(viiva, j);
                            END LOOP;
                          WHEN ST_GeometryType(viiva) = 'ST_Point' THEN
                            tulos := tulos || ST_MakeLine(viiva);
                          ELSE
                            tulos := tulos || viiva;
                        END CASE;
                      END LOOP;
                      RETURN ST_Collect(tulos);
                    END;
                    $$ LANGUAGE plpgsql;")

        y-tunnus (str (gensym))
        vastaus (kutsu y-tunnus)
        ;; Viallisella pisteyhdistelyllä palautuu sisäinen sql virhe
        _ (is (= 500 (:status vastaus)))
        _ (is (=
                (-> vastaus :body (json/read-json) first)
                [:virheet [{:virhe {:koodi "sisainen-kasittelyvirhe", :viesti "Sisäinen käsittelyvirhe"}}]]))]))

(deftest ^:perf yksittainen-kirjaus-ei-kesta-liian-kauan
  (let [sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja)]
    (is (apply
          gatling-onnistuu-ajassa?
          "Yksittäinen reittitoteuma"
          {:timeout-in-ms 3000}
          (take
            10
            (map
              (fn [ulkoinen-id]
                #(tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                   (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                     slurp
                     (.replace "__LAHDE__" "koneellinen")
                     (.replace "__SOPIMUS_ID__" (str sopimus-id))
                     (.replace "__ID__" (str ulkoinen-id))
                     (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy"))))
              (range)))))))

(deftest tallenna-yksittainen-reittitoteuma
  (let [urakka (hae-oulun-alueurakan-2014-2019-id)
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti
                         (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                           slurp
                           (.replace "__LAHDE__" "koneellinen")
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, lahde FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy" "harja-api"]))


      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [_ (anna-kirjoitusoikeus kayttaja-jvh)
            vastaus-paivitys (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-jvh portti
                               (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                                 slurp
                                 (.replace "__LAHDE__" "korjaus")
                                 (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                 (.replace "__ID__" (str ulkoinen-id))
                                 (.replace "__SUORITTAJA_NIMI__" "Peltikoneen Pojat Oy")))]
        (is (= 200 (:status vastaus-paivitys)))

        (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              _ (odota-reittipisteet toteuma-id)
              {reittipisteet ::rp/reittipisteet} (first (fetch ds ::rp/toteuman-reittipisteet
                                                          (columns ::rp/toteuman-reittipisteet)
                                                          {::rp/toteuma-id toteuma-id}))
              toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, lahde FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))
              toteuma-materiaali-idt (into [] (flatten (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuma-id))))
              toteuman-materiaali (ffirst (q (str "SELECT nimi FROM toteuma_materiaali
                                                    JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
                                                    WHERE toteuma = " toteuma-id)))]
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Peltikoneen Pojat Oy", "harja-api-korjaus"]))
          (is (= (count reittipisteet) 3))
          (is (= (count toteuma-tehtava-idt) 3))
          (is (= (count toteuma-materiaali-idt) 1))
          (is (= toteuman-materiaali "Talvisuolaliuos NaCl"))

          (doseq [reittipiste reittipisteet]
            (let [reitti-tehtava-idt (into [] (map ::rp/toimenpidekoodi) (::rp/tehtavat reittipiste))
                  reitti-materiaali-idt (into [] (map ::rp/materiaalikoodi) (::rp/materiaalit reittipiste))
                  reitti-hoitoluokka (::rp/soratiehoitoluokka reittipiste)]
              (is (= (count reitti-tehtava-idt) 3))
              (is (= (count reitti-materiaali-idt) 1))
              (is (= reitti-hoitoluokka 7)))) ; testidatassa on reittipisteen koordinaateille hoitoluokka

          (poista-reittitoteuma toteuma-id ulkoinen-id urakka))))))

(deftest tallenna-yksittainen-reittitoteuma-sama-hash
  (let [urakka (hae-oulun-alueurakan-2014-2019-id)
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja-yit)
        toteumajson (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                      slurp
                      (.replace "__LAHDE__" "koneellinen")
                      (.replace "__SOPIMUS_ID__" (str sopimus-id))
                      (.replace "__ID__" (str ulkoinen-id))
                      (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy"))
        clj-toteuma (cheshire/parse-string toteumajson true)
        hash (konversio/string->md5 (pr-str (:reittitoteuma clj-toteuma)))
        ;; Lähetetään reittitoteuma ensimmäisen kerran
        vastaus1 (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti toteumajson)
        toteuma-kannassa1 (first (q-map (str "SELECT id, json_hash, muokattu FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))

        ;; Lähetetään sama reittitoteuma toisen kerran, pitäisi generoida sama hash ja ilmoittaa vain ok tuloksesta
        vastaus2 (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti toteumajson)
        ;; Vaikka sama toteuma lähetettiin uudestaan, niin hash tarkistuksen takia
        ;; toteumaa ei ole muokattu, joten muokattu aikaleima on null
        toteuma-kannassa2 (first (q-map (str "SELECT id, json_hash, muokattu FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))

        ;; Varmistetaan, että joku hash löytyy tietokannasta
        toteuma-kannassa (first (q-map (str "SELECT id, json_hash FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))

        ;; Tehdään pieni muutos jsoniin ja lähetetään se uudestaan
        ;; Nyt hash pitäisi muuttua ja muokattu -aikaleima päivittyä
        toteumajson2 (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                       slurp
                       (.replace "__LAHDE__" "korjaus")
                       (.replace "__SOPIMUS_ID__" (str sopimus-id))
                       (.replace "__ID__" (str ulkoinen-id))
                       (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy2"))
        vastaus3 (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti toteumajson2)
        toteuma-kannassa3 (first (q-map (str "SELECT id, json_hash, muokattu FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]

    (is (= 200 (:status vastaus1)))
    (is (= 200 (:status vastaus2)))
    (is (= 200 (:status vastaus3)))

    ;; Toteumaa ei ole muokattu, joten muokattu aikaleima on null
    (is (nil? (:muokattu toteuma-kannassa1)))
    (is (nil? (:muokattu toteuma-kannassa2)))
    (is (not (nil? (:muokattu toteuma-kannassa3))))

    (is (= hash (:json_hash toteuma-kannassa1)))
    (is (= hash (:json_hash toteuma-kannassa2)))
    (is (not= hash (:json_hash toteuma-kannassa3)))))

(deftest tallenna-yksittainen-reittitoteuma-sama-hash-poisto-onnistuu
  (let [urakka (hae-oulun-alueurakan-2014-2019-id)
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja-yit)
        toteumajson (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                      slurp
                      (.replace "__LAHDE__" "koneellinen")
                      (.replace "__SOPIMUS_ID__" (str sopimus-id))
                      (.replace "__ID__" (str ulkoinen-id))
                      (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy"))
        clj-toteuma (cheshire/parse-string toteumajson true)
        hash (konversio/string->md5 (pr-str (:reittitoteuma clj-toteuma)))
        ;; Lähetetään reittitoteuma ensimmäisen kerran
        vastaus1 (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti toteumajson)
        toteuma-kannassa1 (first (q-map (str "SELECT id, json_hash, muokattu FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))

        ;; Lähetetään sama reittitoteuma toisen kerran, pitäisi generoida sama hash ja ilmoittaa vain ok tuloksesta
        vastaus2 (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti toteumajson)
        ;; Vaikka sama toteuma lähetettiin uudestaan, niin hash tarkistuksen takia
        ;; toteumaa ei ole muokattu, joten muokattu aikaleima on null
        toteuma-kannassa2 (first (q-map (str "SELECT id, json_hash, muokattu FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))

        ;; Varmistetaan, että joku hash löytyy tietokannasta
        toteuma-kannassa (first (q-map (str "SELECT id, json_hash, poistettu, ulkoinen_id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
        _ (println "toteuma-kannassa: " (pr-str toteuma-kannassa))
        _ (is (not (nil? (:json_hash toteuma-kannassa))))
        _ (is (= false (:poistettu toteuma-kannassa)))
        _ (is (= ulkoinen-id (:ulkoinen_id toteuma-kannassa)))

        ;; Poistetaan toteuma apin kautta
        poistettava-toteuma-json (-> "test/resurssit/api/toteuman-poisto.json"
                                   slurp
                                   (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                   (.replace "__ID__" (str ulkoinen-id))
                                   (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                                   (.replace "__PVM__" (json-tyokalut/json-pvm (Date.))))
        vastaus-poisto (tyokalut/delete-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti
                         poistettava-toteuma-json)
        _ (println "Poistovastaus: " (pr-str vastaus-poisto))

        ;; Varmistetaan, että toteuma on poistettu ja hash on nollattu.
        poistettu-toteuma-db (first (q-map (str "SELECT id, json_hash, poistettu, ulkoinen_id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
        _ (println "poistettu-toteuma-db: " (pr-str poistettu-toteuma-db))
        _ (is (nil? (:json_hash poistettu-toteuma-db)))
        _ (is (= true (:poistettu poistettu-toteuma-db)))
        _ (is (= ulkoinen-id (:ulkoinen_id poistettu-toteuma-db)))

        ;; Tehdään pieni muutos jsoniin ja lähetetään se uudestaan
        ;; Nyt hash pitäisi muuttua ja muokattu -aikaleima päivittyä
        toteumajson2 (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                       slurp
                       (.replace "__LAHDE__" "korjaus")
                       (.replace "__SOPIMUS_ID__" (str sopimus-id))
                       (.replace "__ID__" (str ulkoinen-id))
                       (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy2"))
        vastaus3 (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti toteumajson2)
        toteuma-id3 (:id (first (q-map (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id))))
        _ (odota-reittipisteet toteuma-id3)
        toteuma-kannassa3 (first (q-map (str "SELECT id, json_hash, muokattu, poistettu, ulkoinen_id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
        ;; Varmistetaan, että uusi tallennettu toteuma saa eri hashin kun ekalla kerralla sama toteuma
        _ (is (not (nil? (:json_hash toteuma-kannassa3))))
        _ (is (not= (:json_hash toteuma-kannassa) (:json_hash toteuma-kannassa3)))
        _ (is (= false (:poistettu toteuma-kannassa3)))
        _ (is (= ulkoinen-id (:ulkoinen_id toteuma-kannassa3)))]

    (is (= 200 (:status vastaus1)))
    (is (= 200 (:status vastaus2)))
    (is (= 200 (:status vastaus3)))

    ;; Toteumaa ei ole muokattu, joten muokattu aikaleima on null
    (is (nil? (:muokattu toteuma-kannassa1)))
    (is (nil? (:muokattu toteuma-kannassa2)))
    (is (not (nil? (:muokattu toteuma-kannassa3))))

    (is (= hash (:json_hash toteuma-kannassa1)))
    (is (= hash (:json_hash toteuma-kannassa2)))
    (is (not= hash (:json_hash toteuma-kannassa3)))))


(deftest tallenna-yksittainen-reittitoteuma-vanhalla-talvisuola-materiaalilla
  (let [urakka (hae-oulun-alueurakan-2014-2019-id)
        ;; Talvisuola, rakeinen NaCl - materiaalikoodi id
        materiaalikoodi-id (ffirst (q (str "select id from materiaalikoodi where nimi = 'Talvisuola, rakeinen NaCl';")))
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        api-payload (slurp "test/resurssit/api/reittitoteuma_yksittainen_talvisuola.json")
        _ (anna-kirjoitusoikeus kayttaja-yit)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti
                         (-> api-payload
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Tiensuolaajat Oy")))
        _ (is (= 200 (:status vastaus-lisays)))
        toteuma-kannassa (first (q-map (str "SELECT id, ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
        toteuma-id (:id toteuma-kannassa)
        _ (odota-reittipisteet toteuma-id)
        {reittipisteet ::rp/reittipisteet} (first (fetch ds ::rp/toteuman-reittipisteet
                                                    (columns ::rp/toteuman-reittipisteet)
                                                    {::rp/toteuma-id toteuma-id}))
        toteuma-kannassa (first (q-map (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
        toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))
        toteuma-materiaali-idt (into [] (flatten (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuma-id))))
        toteuman-materiaali (q-map (str "SELECT nimi, maara FROM toteuma_materiaali
                                                    JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
                                                    WHERE toteuma = " toteuma-id))
        materiaalimaara (apply + (map :maara toteuman-materiaali))
        _ (is (= ulkoinen-id (:ulkoinen_id toteuma-kannassa)))
        _ (is (= "8765432-1" (:suorittajan_ytunnus toteuma-kannassa)))
        _ (is (= "Tiensuolaajat Oy" (:suorittajan_nimi toteuma-kannassa)))
        _ (is (= (count reittipisteet) 4))
        _ (is (= (count toteuma-tehtava-idt) 3))
        _ (is (= (count toteuma-materiaali-idt) 1))
        _ (is (= "Talvisuola, rakeinen NaCl" (:nimi (first toteuman-materiaali))))

        ;; Tarkistetaan, että suolatoteuma_reittipiste -tauluun tulee oikeat merkinnat
        _ (odota-suolatoteuma-reittipisteet toteuma-id)
        suolatoteuma-reittipiste-data (q-map (str "SELECT aika, pohjavesialue, materiaalikoodi, maara, rajoitusalue_id
        FROM suolatoteuma_reittipiste WHERE toteuma = " toteuma-id))

        suolatoteuma-reittipiste-materiaalimaara (apply + (map :maara suolatoteuma-reittipiste-data))
        ;; Tämä testi failasi vanhemmalla triggerillä
        _ (is (= materiaalimaara suolatoteuma-reittipiste-materiaalimaara))]


    ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
    (let [_ (anna-kirjoitusoikeus kayttaja-jvh)
          vastaus-paivitys (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-jvh portti
                             (-> "test/resurssit/api/reittitoteuma_yksittainen_talvisuola_paivitys.json"
                               slurp
                               (.replace "__SOPIMUS_ID__" (str sopimus-id))
                               (.replace "__ID__" (str ulkoinen-id))
                               (.replace "__SUORITTAJA_NIMI__" "Peltikoneen Pojat Oy")))]
      (is (= 200 (:status vastaus-paivitys)))

      (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
            _ (odota-reittipisteet toteuma-id)
            {reittipisteet ::rp/reittipisteet} (first (fetch ds ::rp/toteuman-reittipisteet
                                                        (columns ::rp/toteuman-reittipisteet)
                                                        {::rp/toteuma-id toteuma-id}))
            toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, lahde FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
            toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))
            toteuma-materiaali-idt (into [] (flatten (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuma-id))))
            toteuman-paivitetty-materiaali (q-map (str "SELECT nimi, maara FROM toteuma_materiaali
                                                    JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
                                                    WHERE toteuma = " toteuma-id))
            materiaalimaara-paivitetty (apply + (map :maara toteuman-paivitetty-materiaali))]
        (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Peltikoneen Pojat Oy" "harja-api-korjaus"]))
        (is (= (count reittipisteet) 4))
        (is (= (count toteuma-tehtava-idt) 3))
        (is (= (count toteuma-materiaali-idt) 1))
        (is (not= materiaalimaara-paivitetty materiaalimaara))

        ;; Ensimmäisellä pisteellä ei tarkoituksella materiaaleja, lopuilla pitäisi olla.
        (doseq [reittipiste (rest reittipisteet)]
          (let [reitti-tehtava-idt (into [] (map ::rp/toimenpidekoodi) (::rp/tehtavat reittipiste))
                reitti-materiaali-idt (into [] (map ::rp/materiaalikoodi) (::rp/materiaalit reittipiste))
                reitti-hoitoluokka (::rp/soratiehoitoluokka reittipiste)]
            (is (= (count reitti-tehtava-idt) 3))
            (is (= (count reitti-materiaali-idt) 1))
            (is (= reitti-hoitoluokka 7)))) ; testidatassa on reittipisteen koordinaateille hoitoluokka
        ))

    ; Päivitetään toteuman materiaalit nollaksi. Simuloi tilannetta, jossa urakoitsija on vahingossa merkinnyt
    ; suolaa johonkin pisteeseen ja haluaa myöhemmin päivittää luvut nolliksi. Ilmeisesti toteumien poisto on vaikeaa.
    (let [vastaus-nollapaivitys (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-jvh portti
                                  (-> "test/resurssit/api/reittitoteuma_yksittainen_talvisuola_paivitys_nollaksi.json"
                                    slurp
                                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                    (.replace "__ID__" (str ulkoinen-id))
                                    (.replace "__SUORITTAJA_NIMI__" "Kutomakoneen Ajomiehet Oy")))]
      (is (= 200 (:status vastaus-nollapaivitys)))

      (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
            _ (odota-reittipisteet toteuma-id)
            {reittipisteet ::rp/reittipisteet} (first (fetch ds ::rp/toteuman-reittipisteet
                                                        (columns ::rp/toteuman-reittipisteet)
                                                        {::rp/toteuma-id toteuma-id}))
            toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, lahde FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
            toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))
            toteuma-materiaali-idt (into [] (flatten (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuma-id))))
            toteuman-paivitetty-materiaali (q-map (str "SELECT nimi, maara FROM toteuma_materiaali
                                                    JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
                                                    WHERE toteuma = " toteuma-id))
            materiaalimaara-paivitetty (apply + (map :maara toteuman-paivitetty-materiaali))]
        (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Kutomakoneen Ajomiehet Oy" "harja-api-korjaus"]))
        (is (= (count reittipisteet) 4))
        (is (= (count toteuma-tehtava-idt) 3))
        (is (= (count toteuma-materiaali-idt) 1))
        (is (= 0M materiaalimaara-paivitetty))

        ;; Ensimmäisellä pisteellä ei tarkoituksella materiaaleja, lopuilla pitäisi olla.
        (doseq [reittipiste (rest reittipisteet)]
          (let [reitti-tehtava-idt (into [] (map ::rp/toimenpidekoodi) (::rp/tehtavat reittipiste))
                reitti-materiaali-idt (into [] (map ::rp/materiaalikoodi) (::rp/materiaalit reittipiste))
                reitti-hoitoluokka (::rp/soratiehoitoluokka reittipiste)]
            (is (= (count reitti-tehtava-idt) 3))
            (is (= (count reitti-materiaali-idt) 1))
            (is (= reitti-hoitoluokka 7)))) ; testidatassa on reittipisteen koordinaateille hoitoluokka
        ))

    ;; Poistetaan toteuma kannasta
    (poista-reittitoteuma toteuma-id ulkoinen-id urakka)))

(deftest tallenna-soratiehoitoluokalle-reittitoteuma
  (let [urakka (hae-oulun-alueurakan-2014-2019-id)
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja-yit)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti
                         (-> "test/resurssit/api/reittitoteuma_soratie_polyntorjunta.json"
                           slurp
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Suolaajat Oy Ab")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Suolaajat Oy Ab"]))

      (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
            _ (odota-reittipisteet toteuma-id)
            {reittipisteet ::rp/reittipisteet} (first (fetch ds ::rp/toteuman-reittipisteet
                                                        (columns ::rp/toteuman-reittipisteet)
                                                        {::rp/toteuma-id toteuma-id}))
            toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
            toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))
            toteuma-materiaali-idt (into [] (flatten (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuma-id))))
            toteuman-materiaali (ffirst (q (str "SELECT nimi FROM toteuma_materiaali
                                                    JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
                                                    WHERE toteuma = " toteuma-id)))]
        (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Suolaajat Oy Ab"]))
        (is (= (count reittipisteet) 2))
        (is (= (count toteuma-tehtava-idt) 1))
        (is (= (count toteuma-materiaali-idt) 1))
        (is (= toteuman-materiaali "Kesäsuola sorateiden pölynsidonta"))

        (doseq [reittipiste reittipisteet]
          (let [reitti-tehtava-idt (into [] (map ::rp/toimenpidekoodi) (::rp/tehtavat reittipiste))
                reitti-materiaali-idt (into [] (map ::rp/materiaalikoodi) (::rp/materiaalit reittipiste))
                reitti-hoitoluokka (::rp/soratiehoitoluokka reittipiste)]
            (is (= (count reitti-tehtava-idt) 1))
            (is (= (count reitti-materiaali-idt) 1))
            (is (= reitti-hoitoluokka 2)))) ; testidatassa on reittipisteen koordinaateille hoitoluokka

        (poista-reittitoteuma toteuma-id ulkoinen-id urakka)))))

(deftest tallenna-talvisuolausta-pyoratielle
  (let [urakka (hae-oulun-alueurakan-2014-2019-id)
        kayttaja "yit_pk2"
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (ffirst (q "SELECT id FROM sopimus WHERE urakka = " urakka " AND paasopimus IS NULL"))
        tehtava-id (ffirst (q "SELECT id FROM tehtava WHERE nimi = 'Liukkaudentorjunta suolaamalla (materiaali)'"))
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                         (-> "test/resurssit/api/reittitoteuma_talvisuola_pyoratiella.json"
                           slurp
                           (.replace "__TEHTAVA_ID__" (str tehtava-id))
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Suolaajat Oy Ab")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          _ (odota-reittipisteet toteuma-id)
          {reittipisteet ::rp/reittipisteet} (first (fetch ds ::rp/toteuman-reittipisteet
                                                      (columns ::rp/toteuman-reittipisteet)
                                                      {::rp/toteuma-id toteuma-id}))
          toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))
          toteuma-materiaali-idt (into [] (flatten (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuma-id))))
          toteuman-materiaali (ffirst (q (str "SELECT nimi FROM toteuma_materiaali
                                                    JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
                                                    WHERE toteuma = " toteuma-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Suolaajat Oy Ab"]))
      (is (= (count reittipisteet) 4))
      (is (= (count toteuma-tehtava-idt) 1))
      (is (= (count toteuma-materiaali-idt) 1))
      (is (= toteuman-materiaali "Talvisuola, rakeinen NaCl"))

      (doseq [reittipiste reittipisteet]
        (let [reitti-tehtava-idt (into [] (map ::rp/toimenpidekoodi) (::rp/tehtavat reittipiste))
              reitti-materiaali-idt (into [] (map ::rp/materiaalikoodi) (::rp/materiaalit reittipiste))
              reitti-hoitoluokka (::rp/talvihoitoluokka reittipiste)]
          (is (= (count reitti-tehtava-idt) 1))
          (is (= (count reitti-materiaali-idt) 1))
          ;; Varmista, että reitipiste kohdistuu ajoväylälle, eikä kävelytielle.
          ;; Osa pisteistä osuu lähemmäksi ajoväylän vieressä olevalle kevyen liikenteen väylälle.
          (is (= reitti-hoitoluokka 6))))

      (poista-reittitoteuma toteuma-id ulkoinen-id urakka))))

(deftest tallenna-usea-reittitoteuma
  (let [urakka (hae-oulun-alueurakan-2014-2019-id)
        ulkoiset-idt (tyokalut/hae-usea-vapaa-toteuma-ulkoinen-id 2)
        ulkoinen-id-1 (first ulkoiset-idt)
        ulkoinen-id-2 (second ulkoiset-idt)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja-yit)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti
                         (-> "test/resurssit/api/toteumat/reittitoteuma_monta.json"
                           slurp
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID1__" (str ulkoinen-id-1))
                           (.replace "__SUORITTAJA1_NIMI__" "Tienpesijät Oy")
                           (.replace "__ID2__" (str ulkoinen-id-2))
                           (.replace "__SUORITTAJA2_NIMI__" "Tienraivaajat Oy")))
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-2)))]
    (odota-reittipisteet toteuma-id)
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma1-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, lahde FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-1)))]
      (is (= toteuma1-kannassa [ulkoinen-id-1 "8765432-1" "Tienpesijät Oy" "harja-api"])))
    (let [toteuma2-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, lahde FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-2)))]
      (is (= toteuma2-kannassa [ulkoinen-id-2 "8765432-1" "Tienraivaajat Oy" "harja-api"])))))

(deftest tarkista-toteuman-tallentaminen-paasopimukselle
  (let [urakka (hae-oulun-alueurakan-2014-2019-id)
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti
                         (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                           slurp
                           (.replace "__LAHDE__" "koneellinen")
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuman-sopimus-id (ffirst (q (str "SELECT sopimus FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= sopimus-id toteuman-sopimus-id) "Toteuma kirjattiin pääsopimukselle")
      (poista-reittitoteuma toteuma-id ulkoinen-id urakka))))

(deftest tarkista-toteuman-tallentaminen-ilman-oikeuksia
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        _ (anna-kirjoitusoikeus "LX123456789")
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] "LX123456789" portti
                         (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                           slurp
                           (.replace "__LAHDE__" "koneellinen")
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 403 (:status vastaus-lisays)))))

(deftest tarkista-toteuman-tallentaminen-lisaoikeudella
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        _ (u "INSERT INTO kayttajan_lisaoikeudet_urakkaan (urakka, kayttaja, luoja, luotu) VALUES (" urakka-id ", "
            (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus';")) "," (:id +kayttaja-jvh+) ", NOW());")
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        _ (anna-kirjoitusoikeus kayttaja-yit)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/reitti"] kayttaja-yit portti
                         (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                           slurp
                           (.replace "__LAHDE__" "koneellinen")
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
    (odota-reittipisteet toteuma-id)
    (u "DELETE FROM kayttajan_lisaoikeudet_urakkaan;")
    (is (= 200 (:status vastaus-lisays)))))

(defn laheta-yksittainen-reittitoteuma [urakka-id annettu-kayttaja uusi-aika]
  (let [ulkoinen-id (str (tyokalut/hae-vapaa-toteuma-ulkoinen-id))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        _ (anna-kirjoitusoikeus annettu-kayttaja)
        vastaus (tyokalut/post-kutsu
                  ["/api/urakat/" urakka-id "/toteumat/reitti"] annettu-kayttaja portti
                  (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                    slurp
                    (.replace "2016-01-30" uusi-aika)
                    (.replace "__LAHDE__" "koneellinen")
                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                    (.replace "__ID__" (str ulkoinen-id))
                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 200 (:status vastaus)) "Reittitoteuman tallennus onnistuu")
    ulkoinen-id))

(defn poista-toteuma [ulkoinen-id urakka-id annettu-kayttaja]
  (let [_ (anna-kirjoitusoikeus annettu-kayttaja)
        vastaus (tyokalut/delete-kutsu
                  ["/api/urakat/" urakka-id "/toteumat/reitti"]
                  annettu-kayttaja portti
                  (-> "test/resurssit/api/toteuman-poisto.json"
                    slurp
                    (.replace "__ID__" (str ulkoinen-id))
                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                    (.replace "__PVM__" (json-tyokalut/json-pvm (java.util.Date.)))))]
    (is (= 200 (:status vastaus)) "Toteuman poisto onnistuu")))

(defn laheta-yksittainen-reittitoteuma-materiaalilla [urakka-id kayttaja reittitoteuma-materiaali reittipiste1-materiaali reittipiste2-materiaali]
  (let [ulkoinen-id (str (tyokalut/hae-vapaa-toteuma-ulkoinen-id))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        data (-> "test/resurssit/api/reittitoteuma_ilman_materiaalia.json"
               slurp
               (.replace "__SOPIMUS_ID__" (str sopimus-id))
               (.replace "__ID__" (str ulkoinen-id))
               (.replace "__SUORITTAJA_NIMI__" "Materiaalinlaskijat Oy")
               (.replace "__REITTITOTEUMA_MATERIAALIT__" (json/write-str reittitoteuma-materiaali))
               (.replace "__REITTIPISTE1_MATERIAALIT__" (json/write-str reittipiste1-materiaali))
               (.replace "__REITTIPISTE2_MATERIAALIT__" (json/write-str reittipiste2-materiaali)))
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus (tyokalut/post-kutsu
                  ["/api/urakat/" urakka-id "/toteumat/reitti"] kayttaja portti data)]
    (is (= 200 (:status vastaus)) "Reittitoteuman tallennus onnistuu")
    ulkoinen-id))

(defn aja-materiaalit-kantaan [materiaali yksikko kokonaismaara piste1_maara piste2_maara]
  (let [kokonaistoteuma {:materiaali materiaali :maara {:yksikko yksikko, :maara kokonaismaara}}
        p1-mat {:materiaali materiaali :maara {:yksikko yksikko, :maara piste1_maara}}
        p2-mat {:materiaali materiaali :maara {:yksikko yksikko, :maara piste2_maara}}
        ;; Päivitetään varalta materiaalin käyttöraporttitaulu
        _ (q-map "SELECT paivita_raportti_toteutuneet_materiaalit();")
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        sopiva-ajankohta "2020-01-02T12:00:00Z"
        hae-materiaalit (fn [urakka-id aika]
                          (q-map (str (format "SELECT kokonaismaara, \"materiaali-id\" FROM raportti_toteutuneet_materiaalit
        WHERE \"urakka-id\" = %s AND paiva > '%s'" urakka-id aika))))
        oulun-materiaalit (hae-materiaalit urakka-id sopiva-ajankohta)]
    (testing "Materiaalin käyttö on tyhjä aluksi"
      (is (empty? oulun-materiaalit)))

    (testing "Materiaalin lisääminen"
      (let [ulkoinen-id (laheta-yksittainen-reittitoteuma-materiaalilla urakka-id kayttaja-yit
                          kokonaistoteuma p1-mat p2-mat)
            _ (q-map "SELECT paivita_raportti_toteutuneet_materiaalit();")]
        (let [rivit1 (hae-materiaalit urakka-id sopiva-ajankohta)
              kokonaismaara1 (-> rivit1 first :kokonaismaara)]
          (is (= 1 (count rivit1)))
          (is (=marginaalissa? kokonaismaara1 kokonaismaara) "Materiaali ei täsmää kokonaismäärään")

          (testing "Uusi toteuma samalle päivälle, kasvattaa lukua"
            ;; Lähetetään uusi toteuma, määrän pitää tuplautua ja rivimäärä olla sama
            (let [ulkoinen-id3 (laheta-yksittainen-reittitoteuma-materiaalilla urakka-id kayttaja-yit
                                 kokonaistoteuma p1-mat p2-mat)
                  _ (q-map "SELECT paivita_raportti_toteutuneet_materiaalit();")
                  rivit2 (hae-materiaalit urakka-id sopiva-ajankohta)
                  kokonaismaara2 (-> rivit2 first :kokonaismaara)]
              (is (= 1 (count rivit2)) "rivien määrä pysyy samana")
              (is (=marginaalissa? kokonaismaara2 (* 2 kokonaismaara1)) "Määrä on tuplautunut")

              (testing "Ensimmäisen toteuman poistaminen vähentää määriä"
                (poista-toteuma ulkoinen-id urakka-id kayttaja-yit)

                (let [paivitys (q-map "SELECT paivita_raportti_toteutuneet_materiaalit();")
                      rivit3 (hae-materiaalit urakka-id sopiva-ajankohta)
                      kokonaismaara3 (-> rivit3 first :kokonaismaara)]
                  (is (= 1 (count rivit3)) "Rivejä on sama määrä")
                  (is (=marginaalissa? kokonaismaara3 kokonaismaara) "Määrä on laskenut takaisin")))

              (testing "Kolmannen toteuman poistaminen nollaa määrät"
                (poista-toteuma ulkoinen-id3 urakka-id kayttaja-yit)

                (let [_ (q-map "SELECT paivita_raportti_toteutuneet_materiaalit();")
                      rivit4 (hae-materiaalit urakka-id sopiva-ajankohta)]
                  (is (= 0 (count rivit4)) "Materiaaleja ei ole enää"))))))))))

(deftest materiaalien-ajo-kantaan-onnistuu
  ;; Sorastusmurske
  (aja-materiaalit-kantaan "Sorastusmurske" "t" 0.6 0.1 0.5)
  ;; Talvisuola (muutettu kesäsuolasta talvisuolaan)
  (aja-materiaalit-kantaan "Talvisuolaliuos CaCl2, päällystettyjen teiden pölynsidonta" "t" 5M 2M 3M)
  ;; Murske -> pitäisi muuntua sorastusmurskeeksi
  (aja-materiaalit-kantaan "Murske" "t" 10.2M 5.0M 5.2M)
  ;; Kesäsuola -> pitäisi muuntua Kesäsuola (pölynsidonta)
  (aja-materiaalit-kantaan "Kesäsuola" "t" 8M 2M 6M)
  (aja-materiaalit-kantaan "Kesäsuola sorateiden kevätkunnostus" "t" 1M 0.8 0.2)
  (aja-materiaalit-kantaan "Kaliumformiaattiliuos" "t" 0.9 0.6 0.3)
  (aja-materiaalit-kantaan "Kaliumformiaatti" "t" 4M 2M 2M))

(deftest lahetys-tuntemattomalle-urakalle-ei-toimi []
  (let [ulkoinen-id (str (tyokalut/hae-vapaa-toteuma-ulkoinen-id))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus (tyokalut/post-kutsu
                  ["/api/urakat/" 666 "/toteumat/reitti"] kayttaja portti
                  (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                    slurp
                    (.replace "__LAHDE__" "koneellinen")
                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                    (.replace "__ID__" (str ulkoinen-id))
                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 400 (:status vastaus)) "Statuksena viallinen kutsu")
    (is (.contains (:body vastaus) "Urakkaa id:llä 666 ei löydy"))))

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
        _ (u "INSERT INTO kayttajan_lisaoikeudet_urakkaan (urakka, kayttaja, luoja, luotu) VALUES
        (" oulun-alueurakka-id ", " (ffirst (q (str "SELECT id FROM kayttaja WHERE kayttajanimi = '" kayttaja "'"))) ", " (:id +kayttaja-jvh+) ", NOW()),
        (" kajaanin-alueurakka-id ", " (ffirst (q (str "SELECT id FROM kayttaja WHERE kayttajanimi = '" kayttaja "'"))) ", " (:id +kayttaja-jvh+) ", NOW())")
        _ (u "UPDATE kayttaja SET jarjestelma = TRUE WHERE kayttajanimi= '" kayttaja "'")

        _ (anna-kirjoitusoikeus kayttaja)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" oulun-alueurakka-id "/toteumat/reitti"] kayttaja portti
                         (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                           slurp
                           (.replace "__LAHDE__" "koneellinen")
                           (.replace "__SOPIMUS_ID__" (str oulun-sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Tienharjaajat Oy")))
        _ (is (= 200 (:status vastaus-lisays)))
        toinen-vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" kajaanin-alueurakka-id "/toteumat/reitti"] kayttaja portti
                                (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                                  slurp
                                  (.replace "__LAHDE__" "koneellinen")
                                  (.replace "__SOPIMUS_ID__" (str kajaanin-sopimus-id))
                                  (.replace "__ID__" (str ulkoinen-id))
                                  (.replace "__SUORITTAJA_NIMI__" "Tienharjaajat Oy")))]

    (is (= 200 (:status toinen-vastaus-lisays)))))

;; Testaa että update_toteuma_check_partition-triggeri tallentaa materiaalivalimuisti_paivitystarve-rivin
;; oikein kun toteuman alkanut-kenttä muuttuu. Cache-päivitys tapahtuu vasta yöeräajossa.
(deftest paivita-reittitoteuman-alkupvm
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        _ (anna-kirjoitusoikeus kayttaja-yit)
        toteuma (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                  slurp
                  (.replace "__LAHDE__" "koneellinen")
                  (.replace "__SOPIMUS_ID__" (str sopimus-id))
                  (.replace "__ID__" (str ulkoinen-id))
                  (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy"))
        toteuma-ajat-muokattu (-> toteuma
                                (.replace "2016-01-30T12:00:00Z" "2015-01-01T12:00:00Z")
                                (.replace "2016-01-30T13:00:00Z" "2015-01-01T13:00:00Z")
                                (.replace "2016-01-30T14:00:00Z" "2015-01-01T14:00:00Z"))

        ;; Tallennetaan toteuma aluksi kantaan
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/reitti"] kayttaja-yit portti toteuma)
        _ (is (= 200 (:status vastaus-lisays)))

        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
        _ (odota-reittipisteet toteuma-id)

        muutos-ennen-paivitysta (q (str "SELECT id FROM materiaalivalimuisti_paivitystarve WHERE toteuma_id = " toteuma-id))

        ;; Päivitetään toteuma uudella alkanut-ajalla — triggerin pitäisi luoda materiaalivalimuisti_paivitystarve-rivi
        vastaus-paivitys (tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/reitti"] kayttaja-yit portti toteuma-ajat-muokattu)
        _ (is (= 200 (:status vastaus-paivitys)))

        toteuma-id-paivityksen-jalkeen (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
        _ (odota-reittipisteet toteuma-id-paivityksen-jalkeen)

        muutos-paivityksen-jalkeen (first (q (str "SELECT toteuma_id, urakka_id, toteuma_alkanut_vanha::date
                                                     FROM materiaalivalimuisti_paivitystarve
                                                    WHERE toteuma_id = " toteuma-id-paivityksen-jalkeen)))]

    ;; Ennen päivitystä materiaalivalimuisti_paivitystarve-taulussa ei pitäisi olla riviä tälle toteumalle
    (is (empty? muutos-ennen-paivitysta)
      "materiaalivalimuisti_paivitystarve on tyhjä ennen alkanut-muutosta")

    ;; Päivityksen jälkeen triggerin pitää olla luonut materiaalivalimuisti_paivitystarve-rivi vanhalla pvm:llä
    (is (some? muutos-paivityksen-jalkeen)
      "materiaalivalimuisti_paivitystarve sisältää rivin alkanut-muutoksen jälkeen")
    (is (= urakka-id (second muutos-paivityksen-jalkeen))
      "materiaalivalimuisti_paivitystarve.urakka_id on oikein")
    (is (= #inst "2016-01-29T22:00:00.000-00:00" (last muutos-paivityksen-jalkeen))
      "materiaalivalimuisti_paivitystarve.toteuma_alkanut_vanha on alkuperäinen pvm ennen muutosta")

    (poista-reittitoteuma toteuma-id-paivityksen-jalkeen ulkoinen-id urakka-id)
    (u (str "DELETE FROM materiaalivalimuisti_paivitystarve WHERE toteuma_id = " toteuma-id-paivityksen-jalkeen))))

;; Varmistetaan että suolatoteuma_reittipiste-taulu päivittyy oikein kun reittitoteumaa päivitetään.
(deftest suolarajoitusalueen-toteumat-paivittyy-oikein
  (let [urakka (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        ulkoinen-id (str (tyokalut/hae-vapaa-toteuma-ulkoinen-id))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja-yit)
        api-payload (-> (slurp "test/resurssit/api/reittitoteuma_yksittainen_talvisuola.json")
                      (.replace "__SOPIMUS_ID__" (str sopimus-id))
                      (.replace "__ID__" (str ulkoinen-id))
                      (.replace "__SUORITTAJA_NIMI__" "Tiensuolaajat Oy")

                      ;; Siirretään testikirjauksen pisteet osumaan urakalle määritellylle rajoitusalueelle
                      (.replace "\"x\": 429457.970" "\"x\": 276317.06")
                      (.replace "\"y\": 7199520.271" "\"y\": 6641012.76")

                      (.replace "\"x\": 429451.2124" "\"x\": 276309.04")
                      (.replace "\"y\": 7199520.6102" "\"y\": 6641030.75")

                      (.replace "\"x\": 429449.505" "\"x\": 276265.24")
                      (.replace "\"y\": 7199521.6673" "\"y\": 6641175.52")

                      (.replace "\"x\": 429440.5079" "\"x\": 276257.31")
                      (.replace "\"y\": 7199523.6547" "\"y\": 6641470.08"))
        aika-ennen (edellinen-materiaalin-kayton-paivitys sopimus-id)
        vastaus (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti
                  api-payload)

        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id=" ulkoinen-id)))
        _ (odota-reittipisteet toteuma-id)
       ;; _ (odota-materiaalin-kaytto-paivittynyt sopimus-id aika-ennen)
        suolatoteuma-reittipiste-maara-fn #(ffirst (q (str "SELECT sum(maara) FROM suolatoteuma_reittipiste WHERE toteuma=" toteuma-id " AND rajoitusalue_id is not null")))

        reittipiste-suolamaara-fn #(ffirst (q (str "SELECT sum(mat.maara) FROM toteuman_reittipisteet trp"
                                                " LEFT JOIN LATERAL UNNEST(trp.reittipisteet) rp ON TRUE"
                                                " LEFT JOIN LATERAL UNNEST(rp.materiaalit) mat ON TRUE"
                                                " WHERE toteuma="
                                                toteuma-id)))

        suolatoteuma-reittipiste-suola-1 (suolatoteuma-reittipiste-maara-fn)
        toteuma-reittipiste-suola-1 (reittipiste-suolamaara-fn)

        aika-ennen2 (edellinen-materiaalin-kayton-paivitys sopimus-id)
        vastaus2 (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-yit portti
                   (-> api-payload
                     (.replace "\"maara\": 4.62" "\"maara\": 3.62")
                     (.replace "\"maara\": 1.32" "\"maara\": 0.32")))

        _ (odota-reittipisteet toteuma-id)
       ;; _ (odota-materiaalin-kaytto-paivittynyt sopimus-id aika-ennen2)

        suolatoteuma-reittipiste-suola-2 (suolatoteuma-reittipiste-maara-fn)
        toteuma-reittipiste-suola-2 (reittipiste-suolamaara-fn)]

    (is (= 200 (:status vastaus)))
    (is (= 200 (:status vastaus2)))

    (is (= 4.62M suolatoteuma-reittipiste-suola-1) "Suolan määrä suolatoteuma_reittipiste-taulussa täsmää alussa")
    (is (= 3.62M suolatoteuma-reittipiste-suola-2) "Suolan määrä suolatoteuma_reittipiste-taulussa täsmää lopussa")

    (is (= 4.62M toteuma-reittipiste-suola-1) "Suolan määrä toteuma_reittipiste-taulussa täsmää alussa")
    (is (= 3.62M toteuma-reittipiste-suola-2) "Suolan määrä toteuma_reittipiste-taulussa täsmää alussa")))

(deftest reittitoteuma-paattyneeseen-urakkaan-estetaan
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        myohainen-pvm "2019-10-02T12:00:00+03:00"
        _ (anna-kirjoitusoikeus kayttaja-yit)
        ulkoinen-id (rand-int 100000000)
        vastaus (tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/reitti"] kayttaja-yit portti
                  (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                    slurp
                    (.replace "__LAHDE__" "koneellinen")
                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                    (.replace "__ID__" (str ulkoinen-id))
                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                    (.replace "2016-01-30T12:00:00Z" myohainen-pvm)
                    (.replace "2016-01-30T14:00:00Z" myohainen-pvm)
                    (.replace "2016-01-30T13:00:00Z" myohainen-pvm)))
        virheet (-> vastaus :body (cheshire/decode true) :virheet)
        toteuma-id (ffirst (q (format "SELECT id FROM toteuma WHERE ulkoinen_id = %s AND urakka = %s" ulkoinen-id urakka-id)))]
    (is (= 400 (:status vastaus)))
    (is (= "virheellinen-paivamaara" (-> virheet first :virhe :koodi)))
    (is (empty? (q (format "SELECT id FROM toteuma WHERE ulkoinen_id = %s AND urakka = %s" ulkoinen-id urakka-id))))
    (poista-reittitoteuma toteuma-id ulkoinen-id urakka-id)))

(deftest reittitoteuma-urakan-viimeisena-sallittuna-paivana
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        viimeinen-sallittu-pvm "2019-10-01T12:00:00+03:00"
        _ (anna-kirjoitusoikeus kayttaja-yit)
        ulkoinen-id (rand-int 100000000)
        vastaus (tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/reitti"] kayttaja-yit portti
                  (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                    slurp
                    (.replace "__LAHDE__" "koneellinen")
                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                    (.replace "__ID__" (str ulkoinen-id))
                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                    (.replace "2016-01-30T12:00:00Z" viimeinen-sallittu-pvm)
                    (.replace "2016-01-30T14:00:00Z" viimeinen-sallittu-pvm)
                    (.replace "2016-01-30T13:00:00Z" viimeinen-sallittu-pvm)))
        toteuma-id (ffirst (q (format "SELECT id FROM toteuma WHERE ulkoinen_id = %s AND urakka = %s" ulkoinen-id urakka-id)))]
    (is (= 200 (:status vastaus)))
    (is (some? toteuma-id))
    (poista-reittitoteuma toteuma-id ulkoinen-id urakka-id)))

(deftest reittitoteuma-paivitys-paattyneeseen-urakkaan-sallitaan
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        _ (anna-kirjoitusoikeus kayttaja-yit)
        ulkoinen-id (rand-int 100000000)
        alkuperainen-pvm "2015-05-23T12:00:00Z"
        vastaus (tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/reitti"] kayttaja-yit portti
                  (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                    slurp
                    (.replace "__LAHDE__" "koneellinen")
                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                    (.replace "__ID__" (str ulkoinen-id))
                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                    (.replace "2016-01-30T12:00:00Z" alkuperainen-pvm)
                    (.replace "2016-01-30T14:00:00Z" alkuperainen-pvm)
                    (.replace "2016-01-30T13:00:00Z" alkuperainen-pvm)))
        toteuma-id (ffirst (q (format "SELECT id FROM toteuma WHERE ulkoinen_id = %s AND urakka = %s" ulkoinen-id urakka-id)))]
    (is (= 200 (:status vastaus)))
    (is (some? toteuma-id))
    (let [myohassa-pvm "2019-10-02T15:00:00+03:00"
          paivitys-vastaus (tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/reitti"] kayttaja-yit portti
                             (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                               slurp
                               (.replace "__LAHDE__" "koneellinen")
                               (.replace "__SOPIMUS_ID__" (str sopimus-id))
                               (.replace "__ID__" (str ulkoinen-id))
                               (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                               (.replace "2016-01-30T12:00:00Z" myohassa-pvm)
                               (.replace "2016-01-30T14:00:00Z" myohassa-pvm)
                               (.replace "2016-01-30T13:00:00Z" myohassa-pvm)))]
      (is (= 200 (:status paivitys-vastaus)))
      (is (some? (ffirst (q (format "SELECT id FROM toteuma WHERE ulkoinen_id = %s AND urakka = %s" ulkoinen-id urakka-id)))))
      (poista-reittitoteuma toteuma-id ulkoinen-id urakka-id))))

(deftest toteuman-poisto-merkitsee-tehtavat-ja-materiaalit-poistetuiksi
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        _ (anna-kirjoitusoikeus kayttaja-yit)
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)

        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/reitti"] kayttaja-yit portti
                         (-> "test/resurssit/api/reittitoteuma_yksittainen_talvisuola.json"
                           slurp
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Testiyritys Oy")))
        _ (is (= 200 (:status vastaus-lisays)) "Toteuman luonti onnistui")

        toteuma-kannassa (first (q-map (str "SELECT id, poistettu FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
        toteuma-id (:id toteuma-kannassa)
        _ (is (some? toteuma-id) "Toteuma löytyy kannasta")
        _ (is (not (:poistettu toteuma-kannassa)) "Toteuma ei ole poistettu")

        _ (odota-reittipisteet toteuma-id)

        toteuma-tehtavat-ennen (q-map (str "SELECT id, poistettu FROM toteuma_tehtava WHERE toteuma = " toteuma-id))
        toteuma-materiaalit-ennen (q-map (str "SELECT id, poistettu FROM toteuma_materiaali WHERE toteuma = " toteuma-id))
        
        _ (is (> (count toteuma-tehtavat-ennen) 0) "Toteumalla on tehtäviä")
        _ (is (> (count toteuma-materiaalit-ennen) 0) "Toteumalla on materiaaleja")
        _ (is (every? #(not (:poistettu %)) toteuma-tehtavat-ennen) "Tehtävät eivät ole poistettuja")
        _ (is (every? #(not (:poistettu %)) toteuma-materiaalit-ennen) "Materiaalit eivät ole poistettuja")]

    (testing "Toteuman poisto merkitsee toteuman, tehtävät ja materiaalit poistetuiksi"
      (poista-toteuma ulkoinen-id urakka-id kayttaja-yit)

      (let [toteuma-poiston-jalkeen (first (q-map (str "SELECT id, poistettu FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
            _ (is (some? toteuma-poiston-jalkeen) "Toteuma löytyy edelleen kannasta")
            _ (is (:poistettu toteuma-poiston-jalkeen) "Toteuma on merkitty poistetuksi")

            toteuma-tehtavat-jalkeen (q-map (str "SELECT id, poistettu FROM toteuma_tehtava WHERE toteuma = " toteuma-id))
            _ (is (= (count toteuma-tehtavat-ennen) (count toteuma-tehtavat-jalkeen)) "Tehtävien määrä säilyy")
            _ (is (every? :poistettu toteuma-tehtavat-jalkeen) "Kaikki tehtävät on merkitty poistetuiksi")
            
            toteuma-materiaalit-jalkeen (q-map (str "SELECT id, poistettu FROM toteuma_materiaali WHERE toteuma = " toteuma-id))
            _ (is (= (count toteuma-materiaalit-ennen) (count toteuma-materiaalit-jalkeen)) "Materiaalien määrä säilyy")
            _ (is (every? :poistettu toteuma-materiaalit-jalkeen) "Kaikki materiaalit on merkitty poistetuiksi")]
        
        (poista-reittitoteuma toteuma-id ulkoinen-id urakka-id)))))
