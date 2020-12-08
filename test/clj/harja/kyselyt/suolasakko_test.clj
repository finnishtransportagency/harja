(ns harja.kyselyt.suolasakko-test
  (:require [clojure.test :refer [deftest is testing use-fixtures compose-fixtures]]
            [clojure.core.async :as async]
            [harja.testi :refer :all]
            [harja.kyselyt
             [urakat :as urk-q]
             [raportit :as raportit-q]]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [taoensso.timbre :as log]))


(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)))))
  (raportit-q/paivita_raportti_cachet (:db jarjestelma))
  (async/<!! (async/go-loop
               [k 1]
               (let [materiaali-cache-ajettu? (ffirst (q "SELECT exists(SELECT 1 FROM raportti_toteutuneet_materiaalit)"))]
                 (when (and (not materiaali-cache-ajettu?)
                            (< k 10))
                   (async/<! (async/timeout 1000))
                   (recur (inc k))))))
  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

(use-fixtures :each (compose-fixtures
                      urakkatieto-fixture
                      jarjestelma-fixture))

(deftest laske-urakan-suolasakko
  (let [ur @oulun-alueurakan-2014-2019-id]
    (testing "Testidatan Oulun alueurakka 2014 - 2019 lasketaan oikein"
      (is (== -29760.0M
              (ffirst (q (str "SELECT hoitokauden_suolasakko(" ur ", '2014-10-01','2015-09-30')"))))))))

(defn suolasakko [ur lampotila lampotila-pitka sakko-per-tonni sallittu-maara kaytetty-maara]
  ;; Muokkaa oulun alueurakan testidatan toteumia
  (u (str "UPDATE lampotilat SET keskilampotila = " lampotila ", pitka_keskilampotila_vanha = " lampotila-pitka " WHERE urakka = " ur " AND alkupvm='2014-10-01'"))
  (u (str "UPDATE suolasakko SET maara=" sakko-per-tonni ", talvisuolaraja=" sallittu-maara " WHERE urakka=" ur " AND hoitokauden_alkuvuosi=2014"))

  (u (str "UPDATE suolasakko SET maara=" sakko-per-tonni ", talvisuolaraja=" sallittu-maara " WHERE urakka=" ur " AND hoitokauden_alkuvuosi=2014"))


  (u (str "DELETE FROM toteuma_materiaali WHERE toteuma IN (SELECT id FROM toteuma WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'))"))
  (u (str "DELETE FROM toteuma_tehtava WHERE toteuma IN (SELECT id FROM toteuma WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'))"))
  (u (str "DELETE FROM varustetoteuma WHERE toteuma IN (SELECT id FROM toteuma WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'))"))
  (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma IN (SELECT id FROM toteuma WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'))"))
  (u (str "DELETE FROM toteuma WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019')"))

  (u (str "INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
  VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),(SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null),\n        NOW(), '2014-12-15 13:00:00+02', '2014-12-15 13:01:00+02',\n        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'Suolasakkototeuma');"))
  (u (str "INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara)\nVALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Suolasakkototeuma'), NOW(),\n        (SELECT id FROM materiaalikoodi WHERE nimi='Talvisuola')," kaytetty-maara ");"))

  (raportit-q/paivita_raportti_cachet (:db jarjestelma))
  (let [hae-suolasakko-sql (str "SELECT hoitokauden_suolasakko(" ur ", '2014-10-01','2015-09-30')")]
    (double (ffirst (q hae-suolasakko-sql)))))


(defspec muuta-sakon-maaraa
  100
  ;; Muuta sakon laskennassa käytettyjä arvoja oulun alueurakkaan:
  ;; - lämpötila ja  pitkä lämpötila
  ;; - sakko per ylittävä tonni
  ;; - sallittua käyttömäärää
  ;; - toteumaa
  ;; varmista, että sakko on aina oikein laskettu
  (prop/for-all [;; luodaan lämpötilat -40.0 ja +5.0 välillä
                 lampotila  (gen/fmap #(/ % 10.0) (gen/choose -400 50))
                 lampotila-pitka (gen/fmap #(/ % 10.0) (gen/choose -400 50))
                 sakko-per-tonni (gen/choose 1 100) ; gen/s-pos-int
                 sallittu-maara (gen/choose 1 10000)
                 kaytetty-maara (gen/choose 1 10000)]

                (let [lampotila (bigdec lampotila)
                      lampotila-pitka (bigdec lampotila-pitka)
                      erotus (- lampotila lampotila-pitka)
                      sal (cond
                            (>= erotus 4) (* sallittu-maara 1.30)
                            (>= erotus 3) (* sallittu-maara 1.20)
                            (> erotus 2) (* sallittu-maara 1.10)
                            :default sallittu-maara)
                      laskettu-suolasakko
                      (if (> kaytetty-maara (* sal 1.05))
                        (* sakko-per-tonni (- kaytetty-maara (* sal 1.05)))
                        (if (and (< kaytetty-maara (* 0.95 sallittu-maara))
                                 (<= erotus 4.0))
                          (* sakko-per-tonni (- kaytetty-maara
                                                (* 0.95 sallittu-maara)))
                          0.0))
                      tietokannan-suolasakko
                      (suolasakko @oulun-alueurakan-2014-2019-id
                                  lampotila
                                  lampotila-pitka
                                  sakko-per-tonni
                                  sallittu-maara
                                  kaytetty-maara)]
                  (=marginaalissa? (- laskettu-suolasakko) tietokannan-suolasakko 0.1))))
