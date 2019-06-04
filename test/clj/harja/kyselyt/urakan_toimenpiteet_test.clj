(ns harja.kyselyt.urakan-toimenpiteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.kyselyt.urakan-toimenpiteet :as urakan-toimenpiteet]
            [taoensso.timbre :as log]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-oulun-urakan-toimenpiteet-ja-tehtavat-tasot
    (let [db (tietokanta/luo-tietokanta testitietokanta)
          urakka-id @oulun-alueurakan-2005-2010-id
          maara-kannassa (ffirst (q
                                   (str "SELECT count(*)
                                           FROM toimenpidekoodi t4
                                                LEFT JOIN toimenpidekoodi t3 ON t3.id=t4.emo
                                           WHERE t4.taso = 4 AND
                                                t3.id in (SELECT toimenpide FROM toimenpideinstanssi WHERE urakka = "
                                                          @oulun-alueurakan-2005-2010-id ")")))
         response (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat-tasot db urakka-id)]
    (is (not (nil? response)))
    (is (= (count response) maara-kannassa))

    (doseq [rivi response]
      (is (= (:taso (first rivi)) 1))
      (is (= (:koodi (first rivi)) "23000"))
      (is (= (:taso (nth rivi 1)) 2))
      (is (= (:taso (nth rivi 2)) 3))
      (is (= (:taso (nth rivi 3)) 4)))))

(deftest hae-pudun-urakan-toimenpiteet-ja-tehtavat-tasot 
    (let [db (tietokanta/luo-tietokanta testitietokanta)
       urakka-id @pudasjarven-alueurakan-id
          maara-kannassa (ffirst (q
                                   (str "SELECT count(*)
                                           FROM toimenpidekoodi t4
                                                LEFT JOIN toimenpidekoodi t3 ON t3.id=t4.emo
                                           WHERE t4.taso = 4 AND
                                                t3.id in (SELECT toimenpide FROM toimenpideinstanssi WHERE urakka = "
                                     urakka-id ")")))
       response (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat-tasot db urakka-id)]
     (is (not (nil? response)))
     (is (= (count response) maara-kannassa))))
