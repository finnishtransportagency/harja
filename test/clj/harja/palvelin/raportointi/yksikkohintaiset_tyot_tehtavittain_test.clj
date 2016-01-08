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

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (harja.palvelin.main/with-db  db
                                              (raportti/suorita
                                                db
                                                +kayttaja-jvh+
                                                {:hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
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

(deftest kuukausittaisten-summien-haku-urakalle-palauttaa-testidatan-arvot-oikein
  (let [rivit (harja.palvelin.main/with-db  db
                                            (raportti/muodosta-raportti-urakalle
                                              db
                                              {:konteksti :urakka
                                               :urakka-id (hae-oulun-alueurakan-2005-2010-id)
                                               :alkupvm   (c/to-date (t/local-date 2000 10 10))
                                               :loppupvm  (c/to-date (t/local-date 2030 10 10))}))]
    (is (not (empty? rivit)))
    (let [ajorat (first (filter
                          #(= (:nimi %) "Is 1-ajorat. KVL >15000")
                          rivit))]
      (is (= (:toteutunut_maara ajorat) 30M)))))