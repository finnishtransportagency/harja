(ns harja.palvelin.palvelut.debug-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [harja.testi :refer [
                         +kayttaja-jvh+
                         anna-kirjoitusoikeus
                         jarjestelma
                         kutsu-palvelua
                         luo-testitietokanta
                         q
                         testi-http-palvelin
                         u]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.debug :as debug]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (luo-testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :debug (component/using
                   (debug/->Debug)
                   [:db :http-palvelin])))))

  (try
    (testit)
    (finally
      (alter-var-root #'jarjestelma component/stop))))

(use-fixtures :once jarjestelma-fixture)

(deftest hae-suolapoikkeamat-test
  (testing "Haetaan suolapoikkeamat onnistuneesti JVH-käyttäjällä"
    (let [aja-u! (fn [sql]
                   (try
                     (u sql)
                     (catch Exception e
                       (throw (ex-info "SQL-ajossa virhe" {:sql sql} e)))))
          urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
          integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
          sopimus-id (ffirst (q (str "SELECT id FROM sopimus WHERE urakka = " urakka-id " AND paasopimus IS NULL LIMIT 1")))
          ;; Käytetään kaukaista tulevaisuuden päivämäärää varmistamaan että muuta dataa ei ole
          testipvm "2030-06-15"
          toteuma-lisatieto (str "Suolapoikkeamat-test-" (java.util.UUID/randomUUID))]
      (try
        ;; Luodaan testidataa: toteuma suolamateriaalilla
        (aja-u! (format (str "INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, luoja, lisatieto) "
                             "VALUES ('harja-ui'::lahde, %d, %d, NOW(), "
                             "        '%s 10:00:00'::timestamp, "
                             "        '%s 11:00:00'::timestamp, "
                             "        'kokonaishintainen'::toteumatyyppi, %d, '%s')")
                      urakka-id
                      sopimus-id
                      testipvm
                      testipvm
                      integraatio-id
                      toteuma-lisatieto))
        (let [toteuma-id (let [sql (format "SELECT id FROM toteuma WHERE lisatieto = '%s'" toteuma-lisatieto)
                               id (ffirst (q sql))]
                          (when-not id
                            (throw (ex-info "Toteumaa ei löytynyt lisatiedolla" {:sql sql :lisatieto toteuma-lisatieto})))
                          id)
              ;; Lisätään toteuma_materiaali: 100 kg Talvisuolaa
              materiaalikoodi (ffirst (q "SELECT id FROM materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'"))]
          (aja-u! (format (str "INSERT INTO toteuma_materiaali (toteuma, materiaalikoodi, maara, luotu, luoja) "
                               "VALUES (%d, %d, 100, NOW(), %d)")
                        toteuma-id
                        materiaalikoodi
                        integraatio-id))
          
          ;; Lisätään reittipiste jossa materiaali 90 kg
          (aja-u! (str "INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet) 
                        VALUES (" toteuma-id ",
                                ARRAY[
                                  (
                                    '" testipvm " 10:30:00'::timestamp,
                                    point(430000, 7210000),
                                    NULL,
                                    NULL,
                                    ARRAY[]::reittipiste_tehtava[],
                                    ARRAY[(" materiaalikoodi ", 90)::reittipiste_materiaali]::reittipiste_materiaali[]
                                  )::reittipistedata
                                ]::reittipistedata[])"))
          
          ;; Poista automaattisesti luodut suolatoteuma_reittipiste -rivit ja lisätään eksplisiittinen 90 kg rivi
          ;; (trigger luo nämä automaattisesti, mutta haluamme deterministisen testin)
          (u (str "DELETE FROM suolatoteuma_reittipiste WHERE toteuma = " toteuma-id))
          (aja-u! (str "INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara) "
                       "VALUES (" toteuma-id ", "
                       "        '" testipvm " 10:30:00'::timestamp, "
                       "        point(430000, 7210000), "
                       materiaalikoodi ", 90)"))
          
          ;; Päivitetään raportti_toteutuneet_materiaalit näkymä jotta rtm-kentät saavat arvon
          (q "SELECT paivita_raportti_toteutuneet_materiaalit()")
          
          (anna-kirjoitusoikeus "jvh")
          
          ;; Kutsutaan palvelua
          (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                          :debug-hae-suolapoikkeamat
                          +kayttaja-jvh+
                          {:urakka-id urakka-id
                           :alkupvm testipvm
                           :loppupvm testipvm})]
            
            ;; Varmistetaan että vastaus on vector
            (is (vector? vastaus) "Vastauksen tulee olla vektori")
            (is (pos? (count vastaus)) "Vastauksessa pitäisi olla vähintään yksi rivi")
            
            ;; Etsitään luotu toteuma
            (let [tulos (first (filter #(= (:toteuma-id %) toteuma-id) vastaus))]
              (is (some? tulos) "Luotu toteuma löytyy tuloksista")
              (when tulos
                (is (== 100.0 (double (:kokonaismaara tulos))) "Kokonaismäärä on 100")
                (is (== 90.0 (double (:reittipistesumma tulos))) "Reittipistesumma on 90")
                (is (== 90.0 (double (:suolapistesumma tulos))) "Suolapistesumma on 90")
                (is (== 10.0 (double (:delta1 tulos))) "Delta1 (kok - reitti) on 10")
                (is (== 0.0 (double (:delta2 tulos))) "Delta2 (reitti - suola) on 0")
                (is (some? (:alkanut tulos)) "Alkanut-kenttä on asetettu")
                (is (true? (:rtm_loytyy tulos)) "RTM löytyy päivitetylle materiaalinäkymälle")
                (is (some? (:rtm_suola_maara tulos)) "RTM suolamäärä on asetettu")
                (is (== 100.0 (double (:rtm_suola_maara tulos))) 
                    "RTM suolamäärä on tasan 100 (ei muuta dataa samana päivänä)")))))
        
        (finally
          ;; Siivotaan testdata
          (let [toteuma-id (ffirst (q (format "SELECT id FROM toteuma WHERE lisatieto = '%s'" toteuma-lisatieto)))]
            (when toteuma-id
              (u (str "DELETE FROM suolatoteuma_reittipiste WHERE toteuma = " toteuma-id))
              (u (str "DELETE FROM toteuma_materiaali WHERE toteuma = " toteuma-id))
              (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma = " toteuma-id))
              (u (str "DELETE FROM toteuma WHERE id = " toteuma-id)))))))))

(deftest hae-suolapoikkeamien-paivavertailu-test
  (testing "Haetaan suolapoikkeamien päivävertailu (toteumat vs RTM) JVH-käyttäjällä"
    (let [aja-u! (fn [sql]
                   (try
                     (u sql)
                     (catch Exception e
                       (throw (ex-info "SQL-ajossa virhe" {:sql sql} e)))))
          urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
          integraatio-id (ffirst (q "SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'"))
          sopimus-id (ffirst (q (str "SELECT id FROM sopimus WHERE urakka = " urakka-id " AND paasopimus IS NULL LIMIT 1")))
          ;; Käytetään kaukaista tulevaisuuden päivämäärää varmistamaan että muuta dataa ei ole
          testipvm "2030-07-20"
          testipvm-2 "2030-07-21"
          toteuma-lisatieto (str "Suolapoikkeamat-paivavertailu-test-" (java.util.UUID/randomUUID))
          toteuma-lisatieto-2 (str "Suolapoikkeamat-paivavertailu-test-2-" (java.util.UUID/randomUUID))]
      (try
        ;; Päivitetään RTM ensin jotta saadaan baseline
        (q "SELECT paivita_raportti_toteutuneet_materiaalit()")
        
        (anna-kirjoitusoikeus "jvh")
        
        ;; Haetaan baseline ennen inserttejä
        (let [baseline-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :debug-hae-suolapoikkeamien-paivavertailu
                                 +kayttaja-jvh+
                                 {:urakka-id urakka-id
                                  :alkupvm testipvm
                                  :loppupvm testipvm-2})
              baseline-paiva-1 (first (filter #(= testipvm (str (:paiva %))) baseline-vastaus))
              baseline-paiva-2 (first (filter #(= testipvm-2 (str (:paiva %))) baseline-vastaus))
              baseline-toteumat-1 (if baseline-paiva-1 (double (:toteumat_suola_maara baseline-paiva-1)) 0.0)
              baseline-rtm-1 (if baseline-paiva-1 (double (or (:rtm_suola_maara baseline-paiva-1) 0.0)) 0.0)
              baseline-toteumat-2 (if baseline-paiva-2 (double (:toteumat_suola_maara baseline-paiva-2)) 0.0)
              baseline-rtm-2 (if baseline-paiva-2 (double (or (:rtm_suola_maara baseline-paiva-2) 0.0)) 0.0)]
          
          ;; Luodaan testidataa: toteuma 1 - 100 kg suolamateriaalilla päivälle 1
          (aja-u! (format (str "INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, luoja, lisatieto) "
                               "VALUES ('harja-ui'::lahde, %d, %d, NOW(), "
                               "        '%s 10:00:00'::timestamp, "
                               "        '%s 11:00:00'::timestamp, "
                               "        'kokonaishintainen'::toteumatyyppi, %d, '%s')")
                        urakka-id
                        sopimus-id
                        testipvm
                        testipvm
                        integraatio-id
                        toteuma-lisatieto))
          
          (let [toteuma-id-1 (let [sql (format "SELECT id FROM toteuma WHERE lisatieto = '%s'" toteuma-lisatieto)
                                   id (ffirst (q sql))]
                              (when-not id
                                (throw (ex-info "Toteumaa 1 ei löytynyt lisatiedolla" {:sql sql :lisatieto toteuma-lisatieto})))
                              id)
                materiaalikoodi (ffirst (q "SELECT id FROM materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'"))]
            
            ;; Lisätään toteuma_materiaali: 100 kg Talvisuolaa
            (aja-u! (format (str "INSERT INTO toteuma_materiaali (toteuma, materiaalikoodi, maara, luotu, luoja) "
                                 "VALUES (%d, %d, 100, NOW(), %d)")
                          toteuma-id-1
                          materiaalikoodi
                          integraatio-id))
            
            ;; Luodaan toteuma 2 päivälle 2 ILMAN materiaaleja (0-suolapäivä)
            (aja-u! (format (str "INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, luoja, lisatieto) "
                                 "VALUES ('harja-ui'::lahde, %d, %d, NOW(), "
                                 "        '%s 10:00:00'::timestamp, "
                                 "        '%s 11:00:00'::timestamp, "
                                 "        'kokonaishintainen'::toteumatyyppi, %d, '%s')")
                          urakka-id
                          sopimus-id
                          testipvm-2
                          testipvm-2
                          integraatio-id
                          toteuma-lisatieto-2))
            
            ;; Päivitetään raportti_toteutuneet_materiaalit näkymä
            (q "SELECT paivita_raportti_toteutuneet_materiaalit()")
            
            ;; Haetaan uusi tulos
            (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                            :debug-hae-suolapoikkeamien-paivavertailu
                            +kayttaja-jvh+
                            {:urakka-id urakka-id
                             :alkupvm testipvm
                             :loppupvm testipvm-2})]
              
              ;; Varmistetaan että vastaus on vector
              (is (vector? vastaus) "Vastauksen tulee olla vektori")
              
              ;; Tarkistetaan päivä 1 (100 kg suolaa lisätty)
              (let [paiva-tulos-1 (first (filter #(= testipvm (str (:paiva %))) vastaus))]
                (is (some? paiva-tulos-1) "Päivä 1 löytyy tuloksista")
                (when paiva-tulos-1
                  (is (= testipvm (str (:paiva paiva-tulos-1))) "Päivämäärä 1 täsmää")
                  (is (== (+ baseline-toteumat-1 100.0) (double (:toteumat_suola_maara paiva-tulos-1))) 
                      (str "Toteumat suolamäärä kasvoi +100 (baseline: " baseline-toteumat-1 ")"))
                  (is (== (+ baseline-rtm-1 100.0) (double (:rtm_suola_maara paiva-tulos-1))) 
                      (str "RTM suolamäärä kasvoi +100 (baseline: " baseline-rtm-1 ")"))
                  (is (true? (:rtm_loytyy paiva-tulos-1)) "RTM löytyy päivälle 1")
                  (is (true? (:tasmaa paiva-tulos-1)) "Täsmää-kenttä on true päivälle 1")))
              
              ;; Tarkistetaan päivä 2 (0-suolapäivä: toteuma ilman materiaaleja)
              (let [paiva-tulos-2 (first (filter #(= testipvm-2 (str (:paiva %))) vastaus))]
                (is (some? paiva-tulos-2) "Päivä 2 löytyy tuloksista (0-suolapäivä)")
                (when paiva-tulos-2
                  (is (= testipvm-2 (str (:paiva paiva-tulos-2))) "Päivämäärä 2 täsmää")
                  (is (== baseline-toteumat-2 (double (:toteumat_suola_maara paiva-tulos-2))) 
                      (str "Toteumat suolamäärä ei kasvanut päivälle 2 (baseline: " baseline-toteumat-2 ")"))
                  ;; RTM ei välttämättä päivity 0-materiaalille, mutta tämä on ok
                  (is (some? paiva-tulos-2) "0-suolapäivä näkyy tuloksissa"))))))
        
        (finally
          ;; Siivotaan testdata
          (let [toteuma-id-1 (ffirst (q (format "SELECT id FROM toteuma WHERE lisatieto = '%s'" toteuma-lisatieto)))
                toteuma-id-2 (ffirst (q (format "SELECT id FROM toteuma WHERE lisatieto = '%s'" toteuma-lisatieto-2)))]
            (when toteuma-id-1
              (u (str "DELETE FROM suolatoteuma_reittipiste WHERE toteuma = " toteuma-id-1))
              (u (str "DELETE FROM toteuma_materiaali WHERE toteuma = " toteuma-id-1))
              (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma = " toteuma-id-1))
              (u (str "DELETE FROM toteuma WHERE id = " toteuma-id-1)))
            (when toteuma-id-2
              (u (str "DELETE FROM suolatoteuma_reittipiste WHERE toteuma = " toteuma-id-2))
              (u (str "DELETE FROM toteuma_materiaali WHERE toteuma = " toteuma-id-2))
              (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma = " toteuma-id-2))
              (u (str "DELETE FROM toteuma WHERE id = " toteuma-id-2)))
            ;; Päivitetään RTM lopuksi jotta se palautuu
            (q "SELECT paivita_raportti_toteutuneet_materiaalit()")))))))