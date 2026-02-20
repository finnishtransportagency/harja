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

