(ns harja.palvelin.integraatiot.api.pistetoteuman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.string :as str]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.pistetoteuma :as api-pistetoteuma]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]))

(def kayttaja "destia")
(def kayttaja-yit "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-pistetoteuma (component/using
                        (api-pistetoteuma/->Pistetoteuma)
                        [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-kokonaishintainen-pistetoteuma-koneellinen
  (let [urakka (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja-yit)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja-yit portti
                         (-> "test/resurssit/api/toteumat/pistetoteuma_yksittainen.json"
                           slurp
                           (.replace "__LAHDE__" "koneellinen")
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                           (.replace "__TOTEUMA_TYYPPI__" "kokonaishintainen")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lahde FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy" "kokonaishintainen" "harja-api"]))
      (is (= (count toteuma-tehtava-idt) 1))

      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [vastaus-paivitys (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja-yit portti
                               (-> "test/resurssit/api/toteumat/pistetoteuma_yksittainen.json"
                                 slurp
                                 (.replace "__LAHDE__" "korjaus")
                                 (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                 (.replace "__ID__" (str ulkoinen-id))
                                 (.replace "__SUORITTAJA_NIMI__" "Peltikoneen Pojat Oy")
                                 (.replace "__TOTEUMA_TYYPPI__" "kokonaishintainen")))]
        (is (= 200 (:status vastaus-paivitys)))
        (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lahde FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))]
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Peltikoneen Pojat Oy" "kokonaishintainen" "harja-api-korjaus"]))
          (is (= (count toteuma-tehtava-idt) 1)))

        (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma = " toteuma-id))
        (u (str "DELETE FROM toteuma_tehtava WHERE toteuma = " toteuma-id))
        (u (str "DELETE FROM toteuma WHERE ulkoinen_id = " ulkoinen-id " AND urakka = " urakka))))))

(deftest tallenna-kokonaishintainen-pistetoteuma-kasin
  (let [urakka (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja-yit)
        payload (-> "test/resurssit/api/toteumat/pistetoteuma_yksittainen.json"
                  slurp
                  (.replace "__LAHDE__" "kasin")
                  (.replace "__SOPIMUS_ID__" (str sopimus-id))
                  (.replace "__ID__" (str ulkoinen-id))
                  (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                  (.replace "__TOTEUMA_TYYPPI__" "kokonaishintainen"))
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja-yit portti payload)]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lahde FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy" "kokonaishintainen" "harja-api-ui"]))
      (is (= (count toteuma-tehtava-idt) 1))

      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [vastaus-paivitys (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja-yit portti
                               (-> "test/resurssit/api/toteumat/pistetoteuma_yksittainen.json"
                                 slurp
                                 (.replace "__LAHDE__" "korjaus")
                                 (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                 (.replace "__ID__" (str ulkoinen-id))
                                 (.replace "__SUORITTAJA_NIMI__" "Peltikoneen Pojat Oy")
                                 (.replace "__TOTEUMA_TYYPPI__" "kokonaishintainen")))]
        (is (= 200 (:status vastaus-paivitys)))
        (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lahde FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))]
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Peltikoneen Pojat Oy" "kokonaishintainen" "harja-api-korjaus"]))
          (is (= (count toteuma-tehtava-idt) 1)))

        (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma = " toteuma-id))
        (u (str "DELETE FROM toteuma_tehtava WHERE toteuma = " toteuma-id))
        (u (str "DELETE FROM toteuma WHERE ulkoinen_id = " ulkoinen-id))))))

(defn poista-pistetoteuma [toteuma-id ulkoinen-id urakka-id]
  (when toteuma-id
    (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma = " toteuma-id))
    (u (str "DELETE FROM toteuma_tehtava WHERE toteuma = " toteuma-id)))
  (when (and ulkoinen-id urakka-id)
    (u (str "DELETE FROM toteuma WHERE ulkoinen_id = " ulkoinen-id " AND urakka = " urakka-id))))

(deftest tallenna-usea-pistetoteuma
  (let [urakka (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        ulkoiset-idt (tyokalut/hae-usea-vapaa-toteuma-ulkoinen-id 2)
        ulkoinen-id-1 (first ulkoiset-idt)
        ulkoinen-id-2 (second ulkoiset-idt)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja-yit)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja-yit portti
                                                (-> "test/resurssit/api/toteumat/pistetoteuma_monta.json"
                                                    slurp
                                                    (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                                    (.replace "__ID1__" (str ulkoinen-id-1))
                                                    (.replace "__SUORITTAJA1_NIMI__" "Tienpesijät Oy")
                                                    (.replace "__TOTEUMA1_TYYPPI__" "kokonaishintainen")
                                                    (.replace "__ID2__" (str ulkoinen-id-2))
                                                    (.replace "__SUORITTAJA2_NIMI__" "Tienraivaajat Ry")
                                                    (.replace "__TOTEUMA2_TYYPPI__" "kokonaishintainen")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma1-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-1)))
          toteuma1-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lahde FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-1)))
          toteuma1-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma1-id))))]
      (is (= toteuma1-kannassa [ulkoinen-id-1 "8765432-1" "Tienpesijät Oy" "kokonaishintainen" "harja-api"]))
      (is (= (count toteuma1-tehtava-idt) 1)))
    (let [toteuma2-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-2)))
          toteuma2-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lahde FROM toteuma WHERE ulkoinen_id = " ulkoinen-id-2)))
          toteuma2-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma2-id))))]
      (is (= toteuma2-kannassa [ulkoinen-id-2 "8765432-1" "Tienraivaajat Ry" "kokonaishintainen" "harja-api"]))
      (is (= (count toteuma2-tehtava-idt) 1)))))

(deftest pistetoteuma-paattyneeseen-urakkaan-estetaan
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        ;; Urakka päättyy 2019-09-30, kokeillaan kirjata 2019-10-02 (liian myöhään)
        myohainen-pvm "2019-10-02T12:00:00+03:00"
        _ (anna-kirjoitusoikeus kayttaja-yit)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/piste"] kayttaja-yit portti
                 (-> "test/resurssit/api/toteumat/pistetoteuma_yksittainen.json"
                   slurp
                   (.replace "__LAHDE__" "koneellinen")
                   (.replace "__SOPIMUS_ID__" (str sopimus-id))
                   (.replace "__ID__" (str ulkoinen-id))
                   (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                   (.replace "__TOTEUMA_TYYPPI__" "kokonaishintainen")
                   (.replace "2016-01-30T12:00:00Z" myohainen-pvm)))
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
    (is (= 400 (:status vastaus)) "Uuden pistetoteuman kirjaus päättyneen urakan jälkeen estetään")
    (is (str/includes? (:body vastaus) "virheellinen-paivamaara") "Virhekoodi on virheellinen-paivamaara")
    (is (str/includes? (:body vastaus) "Urakka on päättynyt ja kirjaaminen estetty") "Virheilmoitus kertoo urakan päättymisestä")
    (poista-pistetoteuma toteuma-id ulkoinen-id urakka-id)))

(deftest pistetoteuma-urakan-viimeisena-sallittuna-paivana
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        ;; Urakka päättyy 2019-09-30, viimeinen sallittu päivä on 2019-10-01
        viimeinen-sallittu-pvm "2019-10-01T12:00:00+03:00"
        _ (anna-kirjoitusoikeus kayttaja-yit)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/piste"] kayttaja-yit portti
                 (-> "test/resurssit/api/toteumat/pistetoteuma_yksittainen.json"
                   slurp
                   (.replace "__LAHDE__" "koneellinen")
                   (.replace "__SOPIMUS_ID__" (str sopimus-id))
                   (.replace "__ID__" (str ulkoinen-id))
                   (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                   (.replace "__TOTEUMA_TYYPPI__" "kokonaishintainen")
                   (.replace "2016-01-30T12:00:00Z" viimeinen-sallittu-pvm)))
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
    (is (= 200 (:status vastaus)) "Pistetoteuman kirjaus urakan viimeisenä sallittuna päivänä onnistuu")
    (is toteuma-id "Toteuma tallentui kantaan")
    (poista-pistetoteuma toteuma-id ulkoinen-id urakka-id)))

(deftest pistetoteuma-paivitys-paattyneeseen-urakkaan-sallitaan
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        ;; Luodaan ensin toteuma urakan aikana
        alkuperainen-pvm "2019-09-30T12:00:00+03:00"
        _ (anna-kirjoitusoikeus kayttaja-yit)
        vastaus-luonti (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/piste"] kayttaja-yit portti
                         (-> "test/resurssit/api/toteumat/pistetoteuma_yksittainen.json"
                           slurp
                           (.replace "__LAHDE__" "koneellinen")
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                           (.replace "__TOTEUMA_TYYPPI__" "kokonaishintainen")
                           (.replace "2016-01-30T12:00:00Z" alkuperainen-pvm)))
        toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
    (is (= 200 (:status vastaus-luonti)) "Pistetoteuman luonti urakan aikana onnistuu")
    (is toteuma-id "Toteuma tallentui kantaan")

    ;; Päivitetään toteumaa urakan päättymisen jälkeen - uusi päivämäärä on päättymisen jälkeen
    (let [myohassa-pvm "2019-10-02T12:00:00+03:00"
          vastaus-paivitys (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/toteumat/piste"] kayttaja-yit portti
                             (-> "test/resurssit/api/toteumat/pistetoteuma_yksittainen.json"
                               slurp
                               (.replace "__LAHDE__" "koneellinen")
                               (.replace "__SOPIMUS_ID__" (str sopimus-id))
                               (.replace "__ID__" (str ulkoinen-id))
                               (.replace "__SUORITTAJA_NIMI__" "Päivitetty Nimi")
                               (.replace "__TOTEUMA_TYYPPI__" "kokonaishintainen")
                               (.replace "2016-01-30T12:00:00Z" myohassa-pvm)))
          toteuma-kannassa (first (q (str "SELECT suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= 200 (:status vastaus-paivitys)) "Olemassaolevan pistetoteuman päivitys päättyneen urakan jälkeen sallitaan")
      (is (= "Päivitetty Nimi" (first toteuma-kannassa)) "Toteuma päivittyi kantaan")
      (poista-pistetoteuma toteuma-id ulkoinen-id urakka-id))))
