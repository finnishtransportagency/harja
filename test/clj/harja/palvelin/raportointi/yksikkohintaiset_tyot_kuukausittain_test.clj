(ns harja.palvelin.raportointi.yksikkohintaiset-tyot-kuukausittain-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.kyselyt.urakat :as urk-q]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.raportointi :refer :all]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain :as raportti]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]))

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
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi      :yks-hint-kuukausiraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2005-2010-id)
                                 :parametrit {:alkupvm   (c/to-date (t/local-date 2005 10 10))
                                              :loppupvm  (c/to-date (t/local-date 2010 10 10))}})]
    (is (vector? vastaus))
    (is (= :raportti (first vastaus)))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi      :yks-hint-kuukausiraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm            (c/to-date (t/local-date 2005 10 10))
                                              :loppupvm           (c/to-date (t/local-date 2010 10 10))}})]
    (is (vector? vastaus))
    (is (= :raportti (first vastaus)))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi      :yks-hint-kuukausiraportti
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2005 10 10))
                                              :loppupvm (c/to-date (t/local-date 2010 10 10))}})]
    (is (vector? vastaus))
    (is (= :raportti (first vastaus)))))

; FIXME Miten kutsutaan DB:n kanssa?
#_(deftest kuukausittaisten-summien-haku-urakalle-palauttaa-arvot-oikealta-aikavalilta
  (let [vastaus (raportti/hae-kuukausittaiset-summat
                  db
                  {:konteksti :urakka
                   :urakka-id (hae-oulun-alueurakan-2005-2010-id)
                   :alkupvm   (c/to-date (t/local-date 2000 10 10))
                   :loppupvm  (c/to-date (t/local-date 2030 10 10))})]
    (is (not (empty? vastaus)))
    (is (every? #(and (>= % 2005)
                      (<= % 2010)) (map :vuosi vastaus)))
    (is (every? #(and (>= % 1)
                      (<= % 12)) (map :kuukausi vastaus)))))

; FIXME Miten kutsutaan DB:n kanssa?
#_(deftest kuukausittaisten-summien-haku-urakalle-ei-palauta-tyhjia-toteumia
  (let [vastaus (raportti/hae-kuukausittaiset-summat
                  db
                  {:konteksti :urakka
                   :urakka-id (hae-oulun-alueurakan-2005-2010-id)
                   :alkupvm   (c/to-date (t/local-date 2000 10 10))
                   :loppupvm  (c/to-date (t/local-date 2030 10 10))})]
    (is (not (empty? vastaus)))
    (is (every? #(> % 0) (map :toteutunut_maara vastaus)))))

(deftest kuukausittaisten-summien-yhdistaminen-toimii-urakan-yhdelle-tehtavalle
  (let [rivit [{:kuukausi 10 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 1}
               {:kuukausi 11 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 2}
               {:kuukausi 12 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 3}]
        vastaus (raportti/muodosta-raportin-rivit rivit false)]
    (is (= 1 (count vastaus)))
    (is (= (get (first vastaus) "10 / 05") 1))
    (is (= (get (first vastaus) "11 / 05") 2))
    (is (= (get (first vastaus) "12 / 05") 3))))

(deftest kuukausittaisten-summien-yhdistaminen-toimii-urakan-usealle-tehtavalle
  (let [rivit [{:kuukausi 10 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 1}
               {:kuukausi 11 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 2}
               {:kuukausi 11 :vuosi 2005 :nimi "Suolaus" :yksikko "kg" :suunniteltu_maara 1 :toteutunut_maara 3}]
        vastaus (raportti/muodosta-raportin-rivit rivit false)]
    (is (= 2 (count vastaus)))
    (let [auraus (first (filter #(= (:nimi %) "Auraus") vastaus))
          suolaus (first (filter #(= (:nimi %) "Suolaus") vastaus))]
      (is (= (get auraus "10 / 05") 1))
      (is (= (get auraus "11 / 05") 2))
      (is (= (get suolaus "11 / 05") 3)))))

(deftest kuukausittaisten-summien-yhdistaminen-toimii-urakoittain-usealle-tehtavalle
  (let [rivit [{:kuukausi 10 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 1 :urakka_id 1 :urakka_nimi "Sepon urakka"}
               {:kuukausi 11 :vuosi 2005 :nimi "Suolaus" :yksikko "kg" :suunniteltu_maara 1 :toteutunut_maara 2 :urakka_id 1 :urakka_nimi "Sepon urakka"}
               {:kuukausi 12 :vuosi 2005 :nimi "Suolaus" :yksikko "kg" :suunniteltu_maara 1 :toteutunut_maara 666 :urakka_id 1 :urakka_nimi "Sepon urakka"}
               {:kuukausi 12 :vuosi 2005 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 3 :urakka_id 2 :urakka_nimi "Paavon urakka"}
               {:kuukausi 12 :vuosi 2006 :nimi "Auraus" :yksikko "km" :suunniteltu_maara 1 :toteutunut_maara 123 :urakka_id 2 :urakka_nimi "Paavon urakka"}]
        vastaus (raportti/muodosta-raportin-rivit rivit true)]
    (is (= 3 (count vastaus)))
    (let [sepon-auraus (first (filter #(and (= (:nimi %) "Auraus")
                                            (= (:urakka_nimi %) "Sepon urakka"))
                                      vastaus))
          sepon-suolaus (first (filter #(and (= (:nimi %) "Suolaus")
                                             (= (:urakka_nimi %) "Sepon urakka"))
                                       vastaus))
          paavon-auraus (first (filter #(and (= (:nimi %) "Auraus")
                                             (= (:urakka_nimi %) "Paavon urakka"))
                                       vastaus))]
      (is (= (get sepon-auraus "10 / 05") 1))
      (is (= (get sepon-suolaus "11 / 05") 2))
      (is (= (get sepon-suolaus "12 / 05") 666))
      (is (= (get paavon-auraus "12 / 05") 3))
      (is (= (get paavon-auraus "12 / 06") 123)))))

; FIXME Miten kutsutaan DB:n kanssa?
#_(deftest kuukausittaisten-summien-haku-urakalle-palauttaa-testidatan-arvot-oikein
  (let [rivit (raportti/hae-kuukausittaiset-summat
                db
                {:konteksti :urakka
                 :urakka-id (hae-oulun-alueurakan-2005-2010-id)
                 :alkupvm   (c/to-date (t/local-date 2000 10 10))
                 :loppupvm  (c/to-date (t/local-date 2030 10 10))})
        tulos (raportti/muodosta-raportin-rivit rivit false)]
    (is (not (empty? tulos)))
    (let [ajorat (first (filter
                          #(= (:nimi %) "Is 1-ajorat. KVL >15000")
                          tulos))]
      (is (= (get ajorat "10 / 05") 30M)))))
