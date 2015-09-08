(ns harja.palvelin.palvelut.raportit-test
  (:require [clojure.test :refer :all]

            [harja.kyselyt.urakat :as urk-q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.raportit :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]))


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

(deftest yhdista-saman-paivan-samat-tehtavat-toimii-oikein
  (let [yhdistettavat [{:toteutunut_maara 1 :alkanut (t/now) :toimenpidekoodi_id 1 :nimi "Auraus"} ; Pitää yhdistää alempaan
                       {:toteutunut_maara 2 :alkanut (t/now) :toimenpidekoodi_id 1 :nimi "Auraus"}
                       {:toteutunut_maara 44 :alkanut (t/plus (t/now) (t/days 2)) :toimenpidekoodi_id 1 :nimi "Auraus"}
                       {:toteutunut_maara 76 :alkanut (t/plus (t/now) (t/days 2)) :toimenpidekoodi_id 2 :nimi "Suolaus"}
                       {:toteutunut_maara 6 :alkanut (t/plus (t/now) (t/days 4)) :toimenpidekoodi_id 2 :nimi "Suolaus"} ; Pitää yhdistää alempaan
                       {:toteutunut_maara 6 :alkanut (t/plus (t/now) (t/days 4)) :toimenpidekoodi_id 2 :nimi "Suolaus"}
                       {:toteutunut_maara 7 :alkanut (t/plus (t/now) (t/days 5)) :toimenpidekoodi_id 2 :nimi "Suolaus"}
                       {:toteutunut_maara 1 :alkanut (t/plus (t/now) (t/days 4)) :toimenpidekoodi_id 3 :nimi "Paikkaus"} ; Pitää yhdistää kahteen alempaan
                       {:toteutunut_maara 10 :alkanut (t/plus (t/now) (t/days 4)) :toimenpidekoodi_id 3 :nimi "Paikkaus"}
                       {:toteutunut_maara 100 :alkanut (t/plus (t/now) (t/days 4)) :toimenpidekoodi_id 3 :nimi "Paikkaus"}]
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


