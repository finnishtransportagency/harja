(ns harja.palvelin.raportointi.yksikkohintaiset-tyot-kuukausittain-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.kyselyt.urakat :as urk-q]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain :as raportti]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (harja.palvelin.main/with-db  db
                                              (raportti/suorita
                                                db
                                                +kayttaja-jvh+
                                                {:urakka-id (hae-oulun-alueurakan-2005-2010-id)
                                                 :alkupvm   (c/to-date (t/local-date 2005 10 10))
                                                 :loppupvm  (c/to-date (t/local-date 2010 10 10))}))]
    (is (vector? vastaus))
    (is (= :raportti (first vastaus)))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (harja.palvelin.main/with-db  db
                                              (raportti/suorita
                                                db
                                                +kayttaja-jvh+
                                                {:alkupvm   (c/to-date (t/local-date 2005 10 10))
                                                 :loppupvm  (c/to-date (t/local-date 2010 10 10))}))]
    (is (vector? vastaus))
    (is (= :raportti (first vastaus)))))

(deftest kuukausittaisten-summien-haku-urakalle-palauttaa-arvot-oikealta-aikavalilta
  (let [vastaus (harja.palvelin.main/with-db  db
                                              (raportti/hae-kuukausittaiset-summat
                                                db
                                                {:konteksti :urakka
                                                 :urakka-id (hae-oulun-alueurakan-2005-2010-id)
                                                 :alkupvm   (c/to-date (t/local-date 2000 10 10))
                                                 :loppupvm  (c/to-date (t/local-date 2030 10 10))}))]
    (is (not (empty? vastaus)))
    (is (every? #(and (>= % 2005)
                      (<= % 2010)) (map :vuosi vastaus)))
    (is (every? #(and (>= % 1)
                      (<= % 12)) (map :kuukausi vastaus)))))

(deftest kuukausittaisten-summien-haku-urakalle-ei-palauta-tyhjia-toteumia
  (let [vastaus (harja.palvelin.main/with-db  db
                                              (raportti/hae-kuukausittaiset-summat
                                                db
                                                {:konteksti :urakka
                                                 :urakka-id (hae-oulun-alueurakan-2005-2010-id)
                                                 :alkupvm   (c/to-date (t/local-date 2000 10 10))
                                                 :loppupvm  (c/to-date (t/local-date 2030 10 10))}))]
    (is (not (empty? vastaus)))
    (is (every? #(> % 0) (map :toteutunut_maara vastaus)))))

(deftest kuukausittaisten-summien-yhdistaminen-toimii
  (let [rivit [{:kuukausi 10 :vuosi 2005 :nimi "Kevätharjaus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 1}
               {:kuukausi 11 :vuosi 2005 :nimi "Kevätharjaus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 2}
               {:kuukausi 12 :vuosi 2005 :nimi "Kevätharjaus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 3}]
        vastaus (harja.palvelin.main/with-db  db
                                              (raportti/muodosta-raportin-rivit rivit false))]
    (is (= 1 (count vastaus)))
    (is (= (get (first vastaus) "10 / 05") 1))
    (is (= (get (first vastaus) "11 / 05") 2))
    (is (= (get (first vastaus) "12 / 05") 3))))