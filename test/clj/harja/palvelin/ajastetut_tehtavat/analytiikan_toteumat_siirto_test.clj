(ns harja.palvelin.ajastetut-tehtavat.analytiikan-toteumat-siirto-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.ajastetut-tehtavat.analytiikan-toteumat :as analytiikan-toteumat]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [clj-time.coerce :as t-coerce]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konversio])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest siirra-analytiikan-toteumat-toimii-vuodelle-2015
  (let [testitietokanta (:db jarjestelma)
        alkupaiva (pvm/luo-pvm 2015 0 1)
        loppupaiva (pvm/luo-pvm 2015 11 31)
        ;; HAetaan vain vuoden 2015 toteumat ja siivotaan varalta kaikki 2015 vuoden mahdollisesti siirretyt toteumat pois
        _ (u "DELETE FROM analytiikka_toteumat WHERE toteuma_alkanut > '2014-12-31' AND toteuma_alkanut < '2016-01-01'")
        _ (u "DELETE FROM ajastetut_tehtavat WHERE alkuaika_valilta >= '2014-12-31' AND loppuaika_valilta <= '2016-01-01'")
        hae-maarat (fn []
                     [(first (first (q "SELECT count(*) FROM toteuma WHERE alkanut > '2014-12-31' AND alkanut < '2016-01-01'")))
                      (first (first (q "SELECT count(*) FROM analytiikka_toteumat WHERE toteuma_alkanut > '2014-12-31' AND toteuma_alkanut < '2016-01-01'")))])
        maarat-alussa (hae-maarat)
        _ (mapv (fn [paiva]
                  (let [alkupaiva (pvm/paivan-alussa paiva)
                        loppupaiva (pvm/paivan-lopussa paiva)]
                    (analytiikan-toteumat/siirra-toteumat testitietokanta (t-coerce/to-sql-time alkupaiva) (t-coerce/to-sql-time loppupaiva))))
            (pvm/paivat-valissa alkupaiva loppupaiva))
        maarat-lopussa (hae-maarat)
        siirretty (first (q "SELECT luotu, toteumatehtavat, toteumamateriaalit FROM analytiikka_toteumat WHERE toteuma_tunniste_id = '1112'"))
        siirron-luotu-aikaleima (first siirretty)
        toteumatehtavat (first (konversio/jsonb->clojuremap (second siirretty)))
        toteumamateriaalit (first (konversio/jsonb->clojuremap (last siirretty)))

        ;; Haetaan kantaan logitetut ajastetut tehtävät
        ajastetut-tehtavat-logit (q-map (format "SELECT * FROM ajastetut_tehtavat
                                      WHERE tyyppi = 'siirra_toteumat_analytiikalle'
                                      AND loppuaika_valilta between '%s' AND '%s'"
                                      alkupaiva loppupaiva))]

    ;; Varmista toteumien siirto
    (is (not (nil? siirron-luotu-aikaleima)) "Toteuman siirron aikaleima on tallentunut.")
    (is (= false (:f7 toteumatehtavat)) "Toteumatehtävän poistettu-tieto löytyy ja on oikein.")
    (is (not (nil? (:f8 toteumatehtavat))) "Toteuman tehtäväriviltä löytyy luontiakaileima.")
    (is (= 18 (:f5 toteumamateriaalit)) "Toteumamateriaalin rivi-id löytyy ja on oikein.")
    (is (not (nil? (:f7 toteumamateriaalit))) "Toteuman materiaaliriviltä löytyy luontiakaileima.")
    (is (> (first maarat-alussa) (second maarat-alussa)))
    (is (= (first maarat-alussa) (first maarat-lopussa)))
    (is (= (first maarat-alussa) (second maarat-lopussa)))

    ;; Varmista ajastetut_tehtavat logitus
    (is (= 363 (count ajastetut-tehtavat-logit)) "Yksi ajastettu tehtävä on logitettu.")
    (is (= true (:onnistunut (first ajastetut-tehtavat-logit))))
    (is (= true (:onnistunut (last ajastetut-tehtavat-logit))))))

(deftest viimeisin-ajokerta-paivittyy-ajastetut-tehtavat-tauluun
  ;; Tämä testi varmistaa, että kun toteumat siirretään analytiikalle,
  ;; ajastetut_tehtavat tauluun kirjataan onnistunut suoritus oikealla ajankohdalla.
  ;; Tämä on tärkeää, jotta seuraava ajokerta tietää mistä jatkaa.
  (let [testitietokanta (:db jarjestelma)

        ;; Määritellään testiajanjakso: yksi päivä vuodelta 2016
        alkupaiva (pvm/luo-pvm 2016 0 1)
        loppupaiva (pvm/luo-pvm 2016 0 1)
        alkuaika-sql (t-coerce/to-sql-time (pvm/paivan-alussa alkupaiva))
        loppuaika-sql (t-coerce/to-sql-time (pvm/paivan-lopussa loppupaiva))

        ;; Siivotaan ensin mahdolliset vanhat testidatat kyseiseltä päivältä
        _ (u "DELETE FROM analytiikka_toteumat WHERE toteuma_alkanut >= '2016-01-01' AND toteuma_alkanut < '2016-01-02'")
        _ (u "DELETE FROM ajastetut_tehtavat WHERE alkuaika_valilta >= '2016-01-01' AND loppuaika_valilta < '2016-01-02'")

        ;; Haetaan tilanne ennen siirtoa - ei pitäisi olla yhtään riviä
        logit-ennen (q-map "SELECT * FROM ajastetut_tehtavat
                            WHERE tyyppi = 'siirra_toteumat_analytiikalle'
                            AND loppuaika_valilta >= '2016-01-01'
                            AND loppuaika_valilta < '2016-01-02'")

        ;; Ajetaan toteumien siirto
        _ (analytiikan-toteumat/siirra-toteumat testitietokanta alkuaika-sql loppuaika-sql)

        ;; Haetaan tilanne siirron jälkeen
        logit-jalkeen (q-map "SELECT * FROM ajastetut_tehtavat
                              WHERE tyyppi = 'siirra_toteumat_analytiikalle'
                              AND loppuaika_valilta >= '2016-01-01'
                              AND loppuaika_valilta < '2016-01-02'")

        ;; Otetaan talteen ensimmäinen (ja ainoa) logirivi
        logi (first logit-jalkeen)]

    ;; Varmista, että ennen siirtoa ei ollut lokeja
    (is (= 0 (count logit-ennen))
        "Ennen siirtoa ei pitäisi olla ajastetut_tehtavat -rivejä testiajanjaksolle")

    ;; Varmista, että siirron jälkeen on täsmälleen yksi lokirivi
    (is (= 1 (count logit-jalkeen))
        "Siirron jälkeen pitäisi olla täsmälleen yksi ajastetut_tehtavat -rivi")

    ;; Tarkista, että siirto merkittiin onnistuneeksi
    (is (= true (:onnistunut logi))
        "Siirto pitää olla merkitty onnistuneeksi")

    ;; Tarkista, että virhe-kenttä on nil (ei virhettä)
    (is (nil? (:virhe logi))
        "Onnistuneessa siirrossa ei saa olla virhe-kenttää")

    ;; Tarkista, että suoritusyritys_aika on asetettu (tämä on se 'viimeisin ajokerta')
    (is (not (nil? (:loppuaika_valilta logi)))
        "Suoritusyritys_aika (viimeisin ajokerta) pitää olla asetettu")))


