(ns harja.palvelin.palvelut.raportit-test
  (:require [clojure.test :refer :all]

            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.raportit :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :yksikkohintaisten-toiden-kuukausiraportti (component/using
                                                                     (->Raportit)
                                                                     [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn vaadi-urakan-samat-materiaalit-summattu
  "Tarkistaa, ettei joukossa ole saman urakan samoja materiaaleja (eli nämä on summattu yhteen)"
  [materiaalitoteumat]
  (is (= (count (keys (group-by
                        #(select-keys % [:urakka_nimi :materiaali_nimi])
                        materiaalitoteumat))) (count materiaalitoteumat))))

(defn d [txt]
  (.parse (java.text.SimpleDateFormat. "dd.MM.yyyy") txt))

(defn dt [txt]
  (.parse (java.text.SimpleDateFormat. "dd.MM.yyyy HH:mm") txt))

(deftest yhdista-saman-paivan-samat-tehtavat-toimii-oikein
  (let [nyt (java.util.Date.)
        yhdistettavat [{:toteutunut_maara 1 :alkanut nyt :toimenpidekoodi_id 1 :nimi "Auraus"} ; Pitää yhdistää alempaan
                       {:toteutunut_maara 2 :alkanut nyt :toimenpidekoodi_id 1 :nimi "Auraus"}
                       {:toteutunut_maara 44 :alkanut (d "18.8.2015") :toimenpidekoodi_id 1 :nimi "Auraus"}
                       {:toteutunut_maara 76 :alkanut (d "18.8.2015") :toimenpidekoodi_id 2 :nimi "Suolaus"}
                       {:toteutunut_maara 6 :alkanut (d "11.10.2000") :toimenpidekoodi_id 2 :nimi "Suolaus"} ; Pitää yhdistää alempaan
                       {:toteutunut_maara 6 :alkanut (d "11.10.2000") :toimenpidekoodi_id 2 :nimi "Suolaus"}
                       {:toteutunut_maara 7 :alkanut (d "11.9.2015")  :toimenpidekoodi_id 2 :nimi "Suolaus"}
                       {:toteutunut_maara 1 :alkanut (d "11.10.2000") :toimenpidekoodi_id 3 :nimi "Paikkaus"} ; Pitää yhdistää kahteen alempaan
                       {:toteutunut_maara 10 :alkanut (dt "11.10.2000 12:00") :toimenpidekoodi_id 3 :nimi "Paikkaus"}
                       {:toteutunut_maara 100 :alkanut (d "11.10.2000 23:59") :toimenpidekoodi_id 3 :nimi "Paikkaus"}]
        yhdistetyt (yhdista-saman-paivan-samat-tehtavat yhdistettavat)
        yhdistetyt-auraukset (filter
                               #(= (:nimi %) "Auraus")
                               yhdistetyt)
        yhdistetyt-suolaukset (filter
                                #(= (:nimi %) "Suolaus")
                                yhdistetyt)
        yhdistetyt-paikkaukset (filter
                                 #(= (:nimi %) "Paikkaus")
                                 yhdistetyt)]

    (testing "Rivit on määrällisesti yhdistetty oikein"
      (is (= (count yhdistetyt-auraukset) 2))
      (is (= (count yhdistetyt-suolaukset) 3))
      (is (= (count yhdistetyt-paikkaukset) 1)))
    (testing "Määrät on summattu oikein"
      (is (= (:toteutunut_maara (first yhdistetyt-auraukset)) 3))
      (is (= (:toteutunut_maara (second yhdistetyt-auraukset)) 44))
      (is (= (:toteutunut_maara (first yhdistetyt-suolaukset)) 76))
      (is (= (:toteutunut_maara (second yhdistetyt-suolaukset)) 12))
      (is (= (:toteutunut_maara (nth yhdistetyt-suolaukset 2)) 7))
      (is (= (:toteutunut_maara (first yhdistetyt-paikkaukset)) 111)))))

(deftest yks-hint-toiden-raportin-muodostaminen-toimii
  (let [alkupvm (java.sql.Date. 105 9 1)
        loppupvm (java.sql.Date. 106 10 30)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :yksikkohintaisten-toiden-kuukausiraportti +kayttaja-jvh+
                                    {:urakka-id @oulun-alueurakan-2005-2010-id
                                     :alkupvm alkupvm
                                     :loppupvm loppupvm})]
  (is (>= (count vastaus) 3))))

(deftest materiaaliraportin-muodostaminen-urakalle-toimii
  (let [alkupvm (java.sql.Date. 105 9 1)
        loppupvm (java.sql.Date. 106 10 30)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :materiaaliraportti-urakalle +kayttaja-jvh+
                                {:urakka-id @oulun-alueurakan-2005-2010-id
                                 :alkupvm alkupvm
                                 :loppupvm loppupvm})]
    (is (>= (count vastaus) 3))
    (vaadi-urakan-samat-materiaalit-summattu vastaus)))

(deftest materiaaliraportin-muodostaminen-hallintayksikolle-toimii
  (let [alkupvm (java.sql.Date. 105 9 1)
        loppupvm (java.sql.Date. 106 10 30)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :materiaaliraportti-hallintayksikolle +kayttaja-jvh+
                                {:hallintayksikko-id @pohjois-pohjanmaan-hallintayksikon-id
                                 :alkupvm alkupvm
                                 :loppupvm loppupvm})]
    (is (>= (count vastaus) 3))
    (vaadi-urakan-samat-materiaalit-summattu vastaus)))

(deftest materiaaliraportin-muodostaminen-koko-maalle-toimii
  (let [alkupvm (java.sql.Date. 105 9 1)
        loppupvm (java.sql.Date. 106 10 30)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :materiaaliraportti-koko-maalle +kayttaja-jvh+
                                {:alkupvm alkupvm
                                 :loppupvm loppupvm})]
    (is (>= (count vastaus) 4))
    (vaadi-urakan-samat-materiaalit-summattu vastaus)))
