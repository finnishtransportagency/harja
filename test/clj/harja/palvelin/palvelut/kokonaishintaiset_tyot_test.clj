(ns harja.palvelin.palvelut.kokonaishintaiset-tyot-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            
            [harja.kyselyt.urakat :as urk-q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.kokonaishintaiset-tyot :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start 
                     (component/system-map
                      :db (luo-testitietokanta)
                      :http-palvelin (testi-http-palvelin)
                      :kokonaishintaiset-tyot (component/using
                                  (->Kokonaishintaiset-tyot)
                                  [:http-palvelin :db])))))
  
  (testit)
  (alter-var-root #'jarjestelma component/stop))



(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


;; käyttää testidata.sql:stä tietoa
(deftest kaikki-kokonaishintaiset-tyot-haettu-oikein []
  (let [oulun-alueurakan-sopimus (ffirst (q (str "SELECT id 
                                                 FROM sopimus 
                                                 WHERE urakka = " @oulun-alueurakan-id
                                                 " AND paasopimus IS null")))


        kokonaishintaiset-tyot (filter #(= oulun-alueurakan-sopimus (:sopimus %))
                                        (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :kokonaishintaiset-tyot +kayttaja-jvh+ @oulun-alueurakan-id))
        talvihoidon-tyot (filter #(= "Oulu Talvihoito TP" (:tpi_nimi %)) kokonaishintaiset-tyot)
        sorateiden-tyot (filter #(= "Oulu Sorateiden hoito TP" (:tpi_nimi %)) kokonaishintaiset-tyot)
        oulun-alueurakan-toiden-lkm (ffirst (q (str "SELECT count(*) 
                                                                FROM kokonaishintainen_tyo kt 
                                                                LEFT JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
                                                                WHERE tpi.urakka = " @oulun-alueurakan-id
                                                                " AND sopimus = " oulun-alueurakan-sopimus)))
        
        urakoitsijan-urakanvalvoja (oulun-urakan-urakoitsijan-urakkavastaava)
        ei-ole-taman-urakan-urakoitsijan-urakanvalvoja (ei-ole-oulun-urakan-urakoitsijan-urakkavastaava)
        kokonaishintaiset-tyot-kutsujana-urakoitsija (filter #(= oulun-alueurakan-sopimus (:sopimus %))
                                                             (kutsu-palvelua (:http-palvelin jarjestelma)
                                                                     :kokonaishintaiset-tyot urakoitsijan-urakanvalvoja @oulun-alueurakan-id))]

    (is (= (count kokonaishintaiset-tyot) oulun-alueurakan-toiden-lkm))
    (is (= (count kokonaishintaiset-tyot-kutsujana-urakoitsija) oulun-alueurakan-toiden-lkm))
    (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                  :kokonaishintaiset-tyot ei-ole-taman-urakan-urakoitsijan-urakanvalvoja @oulun-alueurakan-id)))
    (is (= "Oulu Talvihoito TP" (:tpi_nimi (first talvihoidon-tyot))))
    (is (= "Oulu Talvihoito TP" (:tpi_nimi (last talvihoidon-tyot))))
    (is (= oulun-alueurakan-sopimus (:sopimus (first talvihoidon-tyot))))
    (is (= oulun-alueurakan-sopimus (:sopimus (last talvihoidon-tyot))))
    (is (= 3500.0 (:summa (first talvihoidon-tyot))))
    (is (= 3500.0 (:summa (first talvihoidon-tyot))))
    (is (= 3500.0 (:summa (last talvihoidon-tyot))))
    (is (= 1500.0 (:summa (first sorateiden-tyot))))
    (is (= 1500.0 (:summa (first sorateiden-tyot))))
    (is (= 1500.0 (:summa (last sorateiden-tyot))))))
