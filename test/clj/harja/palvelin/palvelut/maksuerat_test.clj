(ns harja.palvelin.palvelut.maksuerat-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.maksuerat :refer :all]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.testi :refer :all]
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

(deftest urakan-maksuerat-haettu-okein []
  (let [maksuerat (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-urakan-maksuerat +kayttaja-jvh+ 1)
        maksuera (first maksuerat)]
    (is (= 1 (count maksuerat)))
    (is (= 1 (:numero maksuera)))
    (is (= "kokonaishintainen" (:tyyppi maksuera)))
    (is (= "Oulu Talvihoito TP" (:nimi (:toimenpideinstanssi maksuera))))))





