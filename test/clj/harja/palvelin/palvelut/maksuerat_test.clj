(ns harja.palvelin.palvelut.maksuerat-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.maksuerat :refer :all]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
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
                        :hae-urakan-maksuerat (component/using
                                                (->Maksuerat)
                                                [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(deftest urakan-maksuerat-haettu-okein-urakalle-1
  (let [maksuerat (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-urakan-maksuerat +kayttaja-jvh+ 1)]
    (is (= 5 (count maksuerat)))
    (is (= [1 1 1 1 1] (mapv #(:id (:toimenpideinstanssi %)) maksuerat)))
    (is (= (count (filter #(= "kokonaishintainen" (:tyyppi %)) maksuerat)) 2))
    (is (= (count (filter #(= "yksikkohintainen" (:tyyppi %)) maksuerat)) 1))
    (is (= (count (filter #(= "bonus" (:tyyppi %)) maksuerat)) 1))
    (is (= (count (filter #(= "akillinen_hoitotyo" (:tyyppi %)) maksuerat)) 1))
    (is (= (count (filter #(nil? (:tila %)) maksuerat)) 2))
    (is (= (count (filter #(= "odottaa_vastausta" (:tila %)) maksuerat)) 1))
    (is (= (count (filter #(= "lahetetty" (:tila %)) maksuerat)) 1))
    (is (= (count (filter #(= "virhe" (:tila %)) maksuerat)) 1))))


