(ns harja.palvelin.raportointi.yksikkohintaiset-tyot-tehtavittain-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.kyselyt.urakat :as urk-q]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-tehtavittain :as raportti]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pdf-vienti (component/using
                                      (pdf-vienti/luo-pdf-vienti)
                                      [:http-palvelin])
                        :raportit (component/using
                                    (raportointi/luo-raportointi)
                                    [:db :pdf-vienti])))))

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
                                 :parametrit {:urakka-id (hae-oulun-alueurakan-2005-2010-id)
                                              :alkupvm   (c/to-date (t/local-date 2005 10 10))
                                              :loppupvm  (c/to-date (t/local-date 2010 10 10))}})]
    (is (vector? vastaus))
    (is (= :raportti (first vastaus)))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi      :yks-hint-kuukausiraportti
                                 :konteksti "hallintayksikko"
                                 :parametrit {:hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                              :alkupvm            (c/to-date (t/local-date 2005 10 10))
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


(deftest tehtavakohtaisten-summien-haku-koko-maalle-palauttaa-testidatan-arvot-oikein
  (let [rivit (raportti/hae-summatut-tehtavat-koko-maalle
                db
                {:alkupvm  (c/to-date (t/local-date 2000 10 10))
                 :loppupvm (c/to-date (t/local-date 2030 10 10))})]

    (is (> (count rivit) 5))
    (let [ajorat (first (filter
                          #(= (:nimi %) "Is 1-ajorat. KVL >15000")
                          rivit))]
      (log/debug ajorat)
      (is (= (:toteutunut_maara ajorat) 78M)))))

(deftest tehtavakohtaisten-summien-haku-urakalle-palauttaa-testidatan-arvot-oikein
  (let [rivit (raportti/hae-summatut-tehtavat-urakalle
                db
                {:konteksti :urakka
                 :urakka-id (hae-oulun-alueurakan-2005-2010-id)
                 :alkupvm   (c/to-date (t/local-date 2000 10 10))
                 :loppupvm  (c/to-date (t/local-date 2030 10 10))})]
    (is (not (empty? rivit)))
    (let [ajorat (first (filter
                          #(= (:nimi %) "Is 1-ajorat. KVL >15000")
                          rivit))]
      (is (= (:toteutunut_maara ajorat) 30M)))))