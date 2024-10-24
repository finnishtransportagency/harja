(ns harja.palvelin.integraatiot.api.reittitoteuman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.api.tyokalut.apurit :as apurit]
            [harja.kyselyt.konversio :as konversio]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.reittitoteuma :as api-reittitoteuma]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [specql.core :refer [fetch columns]]
            [harja.domain.reittipiste :as rp]
            [clojure.data.json :as json]
            [cheshire.core :as cheshire]))

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

(defn poista-reittitoteuma [toteuma-id ulkoinen-id]
  (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma = " toteuma-id))
  (u (str "DELETE FROM toteuma_materiaali WHERE toteuma = " toteuma-id))
  (u (str "DELETE FROM toteuma_tehtava WHERE toteuma = " toteuma-id))
  (u (str "DELETE FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))

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
                                        (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                            slurp
                                            (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                            (.replace "__ID__" (str ulkoinen-id))
                                            (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy"))))
             (range)))))))

(deftest tallenna-yksittainen-reittitoteuma
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                    slurp
                                                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy"]))


      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [_ (anna-kirjoitusoikeus kayttaja-jvh)
            vastaus-paivitys (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-jvh portti
                                                      (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
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
              toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))
              toteuma-materiaali-idt (into [] (flatten (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuma-id))))
              toteuman-materiaali (ffirst (q (str "SELECT nimi FROM toteuma_materiaali
                                                    JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
                                                    WHERE toteuma = " toteuma-id)))]
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Peltikoneen Pojat Oy"]))
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

          (poista-reittitoteuma toteuma-id ulkoinen-id))))))

(deftest tallenna-yksittainen-reittitoteuma-sama-hash
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja)
        toteumajson (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                      slurp
                      (.replace "__SOPIMUS_ID__" (str sopimus-id))
                      (.replace "__ID__" (str ulkoinen-id))
                      (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy"))
        clj-toteuma (cheshire/parse-string toteumajson true)
        hash (konversio/string->md5 (pr-str (:reittitoteuma clj-toteuma)))
        ;; Lähetetään reittitoteuma ensimmäisen kerran
        vastaus1 (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti toteumajson)
        toteuma-kannassa1 (first (q-map (str "SELECT id, json_hash, muokattu FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))

        ;; Lähetetään sama reittitoteuma toisen kerran, pitäisi generoida sama hash ja ilmoittaa vain ok tuloksesta
        vastaus2 (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti toteumajson)
        ;; Vaikka sama toteuma lähetettiin uudestaan, niin hash tarkistuksen takia
        ;; toteumaa ei ole muokattu, joten muokattu aikaleima on null
        toteuma-kannassa2 (first (q-map (str "SELECT id, json_hash, muokattu FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))

        ;; Varmistetaan, että joku hash löytyy tietokannasta
        toteuma-kannassa (first (q-map (str "SELECT id, json_hash FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))

        ;; Tehdään pieni muutos jsoniin ja lähetetään se uudestaan
        ;; Nyt hash pitäisi muuttua ja muokattu -aikaleima päivittyä
        toteumajson2 (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                      slurp
                      (.replace "__SOPIMUS_ID__" (str sopimus-id))
                      (.replace "__ID__" (str ulkoinen-id))
                      (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy2"))
        vastaus3 (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti toteumajson2)
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


(deftest tallenna-yksittainen-reittitoteuma-vanhalla-talvisuola-materiaalilla
  (let [;; Talvisuola, rakeinen NaCl - materiaalikoodi id
        materiaalikoodi-id (ffirst (q (str "select id from materiaalikoodi where nimi = 'Talvisuola, rakeinen NaCl';")))
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        api-payload (slurp "test/resurssit/api/reittitoteuma_yksittainen_talvisuola.json")
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                         (-> api-payload
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Tiensuolaajat Oy")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tiensuolaajat Oy"]))


      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [_ (anna-kirjoitusoikeus kayttaja-jvh)
            vastaus-paivitys (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-jvh portti
                               (-> "test/resurssit/api/reittitoteuma_yksittainen_talvisuola.json"
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
              toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))
              toteuma-materiaali-idt (into [] (flatten (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuma-id))))
              toteuman-materiaali (ffirst (q (str "SELECT nimi FROM toteuma_materiaali
                                                    JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
                                                    WHERE toteuma = " toteuma-id)))]
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Peltikoneen Pojat Oy"]))
          (is (= (count reittipisteet) 4))
          (is (= (count toteuma-tehtava-idt) 3))
          (is (= (count toteuma-materiaali-idt) 1))
          (is (= toteuman-materiaali "Talvisuola, rakeinen NaCl"))

          ;; Ensimmäisellä pisteellä ei tarkoituksella materiaaleja, lopuilla pitäisi olla.
          (doseq [reittipiste (rest reittipisteet)]
            (let [reitti-tehtava-idt (into [] (map ::rp/toimenpidekoodi) (::rp/tehtavat reittipiste))
                  reitti-materiaali-idt (into [] (map ::rp/materiaalikoodi) (::rp/materiaalit reittipiste))
                  reitti-hoitoluokka (::rp/soratiehoitoluokka reittipiste)]
              (is (= (count reitti-tehtava-idt) 3))
              (is (= (count reitti-materiaali-idt) 1))
              (is (= reitti-hoitoluokka 7)))) ; testidatassa on reittipisteen koordinaateille hoitoluokka

          (poista-reittitoteuma toteuma-id ulkoinen-id))))))

(deftest tallenna-soratiehoitoluokalle-reittitoteuma
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
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
            (is (= reitti-hoitoluokka 2))))                 ; testidatassa on reittipisteen koordinaateille hoitoluokka

        (poista-reittitoteuma toteuma-id ulkoinen-id)))))

(deftest tallenna-talvisuolausta-pyoratielle
  (let [urakka (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        kayttaja "yit_pk2"
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (ffirst (q "SELECT id FROM sopimus WHERE urakka = " urakka " AND paasopimus IS NULL"))
        tehtava-id (ffirst (q "SELECT id FROM tehtava WHERE nimi = 'Suolaus'"))
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

      (poista-reittitoteuma toteuma-id ulkoinen-id))))

(deftest tallenna-yksittainen-reittitoteuma-ilman-sopimusta-paivittaa-cachen
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        urakka-id (hae-urakan-id-nimella "Pudasjärven alueurakka 2007-2012")
        sopimus-id (ffirst (q (str "SELECT id FROM sopimus WHERE urakka = " urakka-id " AND paasopimus IS NULL")))
        aika-ennen (edellinen-materiaalin-kayton-paivitys sopimus-id)
        sopimuksen_kaytetty_materiaali-maara-ennen (ffirst (q (str "SELECT count(*) FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id)))
        kaytetty-talvisuolaliuos-odotettu 4.62M
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                (-> "test/resurssit/api/reittitoteuma_yksittainen_ilman_sopimusta.json"
                                                    slurp
                                                    (.replace "__ID__" (str ulkoinen-id))
                                                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
    (odota-reittipisteet toteuma-id)
    (odota-materiaalin-kaytto-paivittynyt sopimus-id aika-ennen)
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          sopimuksen_kaytetty_materiaali-jalkeen (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy"]))
       (is (= 0 sopimuksen_kaytetty_materiaali-maara-ennen))
       (is (= 1 (count sopimuksen_kaytetty_materiaali-jalkeen)))
       (is (= kaytetty-talvisuolaliuos-odotettu (last (first sopimuksen_kaytetty_materiaali-jalkeen)))))))


(deftest tallenna-usea-reittitoteuma
  (let [ulkoiset-idt (tyokalut/hae-usea-vapaa-toteuma-ulkoinen-id 2)
        ulkoinen-id-1 (first ulkoiset-idt)
        ulkoinen-id-2 (second ulkoiset-idt)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                (-> "test/resurssit/api/reittitoteuma_monta.json"
                                                    slurp
                                                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                                    (.replace "__ID1__" (str ulkoinen-id-1))
                                                    (.replace "__SUORITTAJA1_NIMI__" "Tienpesijät Oy")
                                                    (.replace "__ID2__" (str ulkoinen-id-2))
                                                    (.replace "__SUORITTAJA2_NIMI__" "Tienraivaajat Oy")))
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-2)))]
    (odota-reittipisteet toteuma-id)
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma1-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-1)))]
      (is (= toteuma1-kannassa [ulkoinen-id-1 "8765432-1" "Tienpesijät Oy"])))
    (let [toteuma2-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-2)))]
      (is (= toteuma2-kannassa [ulkoinen-id-2 "8765432-1" "Tienraivaajat Oy"])))))

(deftest tarkista-toteuman-tallentaminen-paasopimukselle
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
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
        _ (anna-kirjoitusoikeus "LX123456789")
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] "LX123456789" portti
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
        _ (anna-kirjoitusoikeus "destia")
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] "destia" portti
                                                (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                    slurp
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
                  (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                    slurp
                    (.replace "2016-01-30" uusi-aika)
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

(deftest materiaalin-kaytto-paivittyy-oikein
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        poistetaan-aluksi-materiaalit-cachesta (u "DELETE FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = "urakka-id)
        lasketaan-materiaalicache-uusiksi (q (str "select paivita_urakan_materiaalin_kaytto_hoitoluokittain("urakka-id",'2017-01-01'::DATE,'2100-12-31'::DATE);"))
        ;; Vuonna 2017 ei pitäisi oulun urakalla olla toteumia kannassa
        hae-materiaalit #(q-map "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id " AND extract(year from pvm) = 2017 ")
        materiaalin-kaytto-ennen (hae-materiaalit)]

    (testing "Materiaalin käyttö on tyhjä aluksi"
      (is (empty? materiaalin-kaytto-ennen)))

    (testing "Uuden materiaalitoteuman lähetys lisää päivälle rivin"
      (let [aika-ennen (edellinen-materiaalin-kayton-paivitys sopimus-id)
            ulkoinen-id1 (laheta-yksittainen-reittitoteuma urakka-id kayttaja-yit "2017-01-30")
            toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id1)))]
        (odota-reittipisteet toteuma-id)
        (odota-materiaalin-kaytto-paivittynyt sopimus-id aika-ennen)
        (let [rivit1 (hae-materiaalit)
              maara1 (:maara (first rivit1))]
          (is (= 1 (count rivit1)))
          (is (=marginaalissa? maara1 4.62) "Suolaa 4.62")

          (testing "Uusi toteuma samalle päivälle, kasvattaa lukua"
            ;; Lähetetään uusi toteuma, määrän pitää tuplautua ja rivimäärä olla sama
            (let [aika-ennen2 (edellinen-materiaalin-kayton-paivitys sopimus-id)
                  ulkoinen-id2 (laheta-yksittainen-reittitoteuma urakka-id kayttaja-jvh "2017-01-30")
                  toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id2)))
                  _ (odota-reittipisteet toteuma-id)
                  _ (odota-materiaalin-kaytto-paivittynyt sopimus-id aika-ennen2)
                  rivit2 (hae-materiaalit)
                  maara2 (:maara (first rivit2))]
              (is (= 1 (count rivit2)) "rivien määrä pysyy samana")
              (is (=marginaalissa? maara2 (* 2 maara1)) "Määrä on tuplautunut")))

          (testing "Ensimmäisen toteuman poistaminen vähentää määriä"
            (poista-toteuma ulkoinen-id1 urakka-id kayttaja-yit)

            (let [ ;; Koska suorituksen ajankohta muuttuu niin paljon, niin koko hoitoluokkahistoria pitää päivittää tälle yritykselle
                  _ (q (str "select paivita_urakan_materiaalin_kaytto_hoitoluokittain("urakka-id",'2017-01-01'::DATE,'2100-12-31'::DATE);"))
                  rivit3 (hae-materiaalit)
                  maara3 (:maara (first rivit3))]
              (is (= 1 (count rivit3)) "Rivejä on sama määrä")
              (is (=marginaalissa? maara3 4.62) "Määrä on laskenut takaisin"))))))))

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
  ;; Kesäsuola (päällystettyjen teiden pölynsidonta)
  (aja-materiaalit-kantaan "Kesäsuola päällystettyjen teiden pölynsidonta" "t" 5M 2M 3M )
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

        _ (anna-kirjoitusoikeus kayttaja)
        vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" oulun-alueurakka-id "/toteumat/reitti"] kayttaja portti
                         (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                           slurp
                           (.replace "__SOPIMUS_ID__" (str oulun-sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Tienharjaajat Oy")))
        _ (is (= 200 (:status vastaus-lisays)))
        toinen-vastaus-lisays (tyokalut/post-kutsu ["/api/urakat/" kajaanin-alueurakka-id "/toteumat/reitti"] kayttaja portti
                                (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                  slurp
                                  (.replace "__SOPIMUS_ID__" (str kajaanin-sopimus-id))
                                  (.replace "__ID__" (str ulkoinen-id))
                                  (.replace "__SUORITTAJA_NIMI__" "Tienharjaajat Oy")))]

    (is (= 200 (:status toinen-vastaus-lisays)))))

(deftest paivita-reittitoteuma-monesti-hoitoluokittaiset-summat-paivitetaan-oikein
  (let [ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja)
        reittototeumakutsu-joka-tehdaan-monesti (fn [urakka kayttaja portti sopimus-id ulkoinen-id]
                                                  (let [aika-ennen (edellinen-materiaalin-kayton-paivitys sopimus-id)
                                                        vastaus (tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                                                          (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                                                            slurp
                                                            (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                                            (.replace "__ID__" (str ulkoinen-id))
                                                            (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")))
                                                        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
                                                    (odota-reittipisteet toteuma-id)
                                                    (odota-materiaalin-kaytto-paivittynyt sopimus-id aika-ennen)
                                                    vastaus))
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
              testattava-apitunnus 987654
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
          (is (= (count toteuma-tehtava-idt) 3))
          (is (= (some #(= % testattava-apitunnus) toteuma-tehtava-idt) nil) "Tehtävä-id:ksi ei ole tallennettu apitunnusta (987654) vaan toimenpidekoodin id.")
          (is (= (count toteuma-materiaali-idt) 1))
          (is (= toteuman-materiaali "Talvisuolaliuos NaCl"))

          (doseq [reittipiste reittipisteet]
            (let [reitti-tehtava-idt (into [] (map ::rp/toimenpidekoodi) (::rp/tehtavat reittipiste))
                  reitti-materiaali-idt (into [] (map ::rp/materiaalikoodi) (::rp/materiaalit reittipiste))
                  reitti-hoitoluokka (::rp/soratiehoitoluokka reittipiste)]
              (is (= (count reitti-tehtava-idt) 3))
              (is (= (count reitti-materiaali-idt) 1))
              (is (= reitti-hoitoluokka 7)))) ; testidatassa on reittipisteen koordinaateille hoitoluokka

          (poista-reittitoteuma toteuma-id ulkoinen-id))
        (is (= hoitoluokittaiset-eka-kutsun-jalkeen
               hoitoluokittaiset-toka-kutsun-jalkeen
               hoitoluokittaiset-kolmannen-kutsun-jalkeen) "hoitoluokittaiset samat kaikkien kutsujen jälkeen")
        (is (= sopimuksen-mat-kaytto-eka-kutsun-jalkeen
               sopimuksen-mat-kaytto-toka-kutsun-jalkeen
               sopimuksen-mat-kaytto-kolmannen-kutsun-jalkeen) "sopimuksen mat käyttö samat kaikkien kutsujen jälkeen")))))

;; testaa että update trigger toimii oikein
(deftest paivita-reittitoteuman-alkupvm
  (let [urakka-id (hae-urakan-id-nimella "Pudasjärven alueurakka 2007-2012")
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        aika-ennen (edellinen-materiaalin-kayton-paivitys sopimus-id)
        _ (anna-kirjoitusoikeus kayttaja)
        toteuma (-> "test/resurssit/api/reittitoteuma_yksittainen.json"
                    slurp
                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                    (.replace "__ID__" (str ulkoinen-id))
                    (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy"))
        toteuma-alkanut-muokattu (-> toteuma
                                     (.replace  "2016-01-30T12:00:00Z" "2015-01-01T12:00:00Z")
                                     (.replace  "2016-01-30T13:00:00Z" "2015-01-01T13:00:00Z"))
        reittototeumakutsu-joka-tehdaan-monesti (fn [urakka-id kayttaja portti sopimus-id ulkoinen-id]
                                                  (tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/reitti"] kayttaja portti
                toteuma))

        reittototeumakutsu-jossa-alkanut-muokattu (fn [urakka-id kayttaja portti sopimus-id ulkoinen-id]
                                                    (tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/reitti"] kayttaja portti
                                                                           toteuma-alkanut-muokattu))
        vastaus-lisays (reittototeumakutsu-joka-tehdaan-monesti urakka-id kayttaja portti sopimus-id ulkoinen-id)
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
        _ (odota-reittipisteet toteuma-id)
        _ (odota-materiaalin-kaytto-paivittynyt sopimus-id aika-ennen)
        ;; Päivitetään
        hoitoluokittaiset-eka-kutsun-jalkeen (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara
        FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id " AND pvm > '2014-01-01'::DATE AND pvm < '2016-09-30'::DATE"))
        sopimuksen-mat-kaytto-eka-kutsun-jalkeen (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy"]))

      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [aika-ennen2 (edellinen-materiaalin-kayton-paivitys sopimus-id)
            vastaus-paivitys (reittototeumakutsu-jossa-alkanut-muokattu urakka-id kayttaja portti sopimus-id ulkoinen-id)
            toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
            _ (odota-reittipisteet toteuma-id)
            _ (odota-materiaalin-kaytto-paivittynyt sopimus-id aika-ennen2)
            ;; Koska suorituksen ajankohta muuttuu niin paljon, niin koko hoitoluokkahistoria pitää päivittää tälle yritykselle
            _ (q (str "select paivita_urakan_materiaalin_kaytto_hoitoluokittain("urakka-id",'2014-01-01'::DATE,'2100-12-31'::DATE);"))
            hoitoluokittaiset-toisen-kutsun-jalkeen (q (str "SELECT pvm, materiaalikoodi, talvihoitoluokka, urakka, maara
            FROM urakan_materiaalin_kaytto_hoitoluokittain WHERE urakka = " urakka-id " AND pvm > '2014-01-01'::DATE AND pvm < '2016-09-30'::DATE"))
            sopimuksen-mat-kaytto-toisen-kutsun-jalkeen (q (str "SELECT sopimus, alkupvm, materiaalikoodi, maara FROM sopimuksen_kaytetty_materiaali WHERE sopimus = " sopimus-id))]
        (is (= 200 (:status vastaus-paivitys)))
        (let [testattava-apitunnus 987654
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
          (is (= (count toteuma-tehtava-idt) 3))
          (is (= (some #(= % testattava-apitunnus) toteuma-tehtava-idt) nil) "Tehtävä-id:ksi ei ole tallennettu apitunnusta (987654) vaan toimenpidekoodin id.")
          (is (= (count toteuma-materiaali-idt) 1))
          (is (= toteuman-materiaali "Talvisuolaliuos NaCl"))

          (doseq [reittipiste reittipisteet]
            (let [reitti-tehtava-idt (into [] (map ::rp/toimenpidekoodi) (::rp/tehtavat reittipiste))
                  reitti-materiaali-idt (into [] (map ::rp/materiaalikoodi) (::rp/materiaalit reittipiste))
                  reitti-hoitoluokka (::rp/soratiehoitoluokka reittipiste)]
              (is (= (count reitti-tehtava-idt) 3))
              (is (= (count reitti-materiaali-idt) 1))
              (is (= reitti-hoitoluokka 7)))) ; testidatassa on reittipisteen koordinaateille hoitoluokka

          (poista-reittitoteuma toteuma-id ulkoinen-id))
        ;; hoitoluokittaisten ja sopparin matskun käyttöjen cachen päivittyessä oikein, eivät 1. ja 2. kutsun jälkeiset tilat ole samat
        (is (not= hoitoluokittaiset-eka-kutsun-jalkeen
                  hoitoluokittaiset-toisen-kutsun-jalkeen))
        (is (not= sopimuksen-mat-kaytto-eka-kutsun-jalkeen
               sopimuksen-mat-kaytto-toisen-kutsun-jalkeen))

        (is (= hoitoluokittaiset-eka-kutsun-jalkeen [[#inst "2016-01-29T22:00:00.000-00:00" 1 100 2 4.62M]]) "eka kutsun jälkeen")
        ;; varmista että alkuperäiset on nollattu, ja uuteen pvm:ään puolestaan lisätty määrät
        (is (= hoitoluokittaiset-toisen-kutsun-jalkeen [[#inst "2014-12-31T22:00:00.000-00:00" 1 100 2 4.62M]]) "toisen kutsun jälkeen")
        (is (= sopimuksen-mat-kaytto-eka-kutsun-jalkeen [[5 #inst "2016-01-29T22:00:00.000-00:00" 1 4.62M]])
            "sopimuksen-mat-kaytto-eka-kutsun-jalkeen")
        (is (= sopimuksen-mat-kaytto-toisen-kutsun-jalkeen [[5 #inst "2014-12-31T22:00:00.000-00:00" 1 4.62M]])
            "sopimuksen-mat-kaytto-toisen-kutsun-jalkeen")))))

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
        _ (odota-materiaalin-kaytto-paivittynyt sopimus-id aika-ennen)
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
        _ (odota-materiaalin-kaytto-paivittynyt sopimus-id aika-ennen2)

        suolatoteuma-reittipiste-suola-2 (suolatoteuma-reittipiste-maara-fn)
        toteuma-reittipiste-suola-2 (reittipiste-suolamaara-fn)]

    (is (= 200 (:status vastaus)))
    (is (= 200 (:status vastaus2)))

    (is (= 4.62M suolatoteuma-reittipiste-suola-1) "Suolan määrä suolatoteuma_reittipiste-taulussa täsmää alussa")
    (is (= 3.62M suolatoteuma-reittipiste-suola-2) "Suolan määrä suolatoteuma_reittipiste-taulussa täsmää lopussa")

    (is (= 4.62M toteuma-reittipiste-suola-1) "Suolan määrä toteuma_reittipiste-taulussa täsmää alussa")
    (is (= 3.62M toteuma-reittipiste-suola-2) "Suolan määrä toteuma_reittipiste-taulussa täsmää alussa")))
