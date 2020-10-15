(ns harja.palvelin.raportointi.kulut-tehtavaryhmittain-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi :as raportointi]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [clj-time.coerce :as c]
            [clj-time.core :as t]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pdf-vienti (component/using
                                      (pdf-vienti/luo-pdf-vienti)
                                      [:http-palvelin])
                        :raportointi (component/using
                                       (raportointi/luo-raportointi)
                                       [:db :pdf-vienti])
                        :raportit (component/using
                                    (raportit/->Raportit)
                                    [:http-palvelin :db :raportointi :pdf-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      urakkatieto-fixture
                      jarjestelma-fixture))

(deftest kulut-tehtavaryhmittain-testi
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :kulut-tehtavaryhmittain
                                 :konteksti  "urakka"
                                 :urakka-id  @oulun-maanteiden-hoitourakan-2019-2024-id
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2019 12 1))
                                              :loppupvm (c/to-date (t/local-date 2020 8 30))}})
        vastaus-ulkopuolella (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :kulut-tehtavaryhmittain
                                 :konteksti  "urakka"
                                 :urakka-id  @oulun-maanteiden-hoitourakan-2019-2024-id
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2014 12 1))
                                              :loppupvm (c/to-date (t/local-date 2015 8 30))}})
        yhteensa (some #(when (= "Yhteensä" (first %)) %)
                       (-> vastaus
                           (nth 2)
                           (nth 3)))
        eka-luku (second yhteensa)
        toka-luku (nth yhteensa 2)
        numeroksi (fn [arvo]
                    (if (string? arvo)
                      (Integer/parseInt arvo)
                      arvo))
        raportti-avainsana (first vastaus)
        taulukot (nth vastaus 2)
        taulukko-avainsana (first taulukot)
        taulukon-rivit (-> vastaus
                           (nth 2)
                           (nth 3))]
    (is (and
          (= 10 (numeroksi 10))
          (= 10 (numeroksi "10"))) "Testataan apufunkkari")
    (is (vector? vastaus) "Raportille palautuu tavaraa")
    (is (and (= :raportti raportti-avainsana)
             (= :taulukko taulukko-avainsana)
             (vector? taulukon-rivit)
             (> (count taulukon-rivit)
                0)) "Vastaus näyttää raportilta")
    (is (and
          (> toka-luku 0)
          (> eka-luku 0)) "Raportille lasketaan summat oikein (jos testidata muuttuu, tää voi kosahtaa)")
    (is (every? #(let [eka (numeroksi (second %))
                       toka (numeroksi (nth % 2))]
                   (= 0 eka toka))
                (-> vastaus-ulkopuolella
                    (nth 2)
                    (nth 3))) "Raportille ei tule väärää tavaraa")))