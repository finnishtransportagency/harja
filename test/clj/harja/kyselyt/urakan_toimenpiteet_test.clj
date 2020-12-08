(ns harja.kyselyt.urakan-toimenpiteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.kyselyt.urakan-toimenpiteet :as urakan-toimenpiteet]
            [taoensso.timbre :as log]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (urakkatieto-alustus!)
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!)
  (urakkatieto-lopetus!))


(use-fixtures :once jarjestelma-fixture)

(deftest hae-oulun-urakan-toimenpiteet-ja-tehtavat-tasot
    (let [db (:db jarjestelma)
          urakka-id @oulun-alueurakan-2005-2010-id
          maara-kannassa (- (ffirst (q
                                   (str "SELECT count(*)
                                           FROM toimenpidekoodi t4
                                                LEFT JOIN toimenpidekoodi t3 ON t3.id=t4.emo
                                           WHERE t4.taso = 4 AND
                                                t3.id in (SELECT toimenpide FROM toimenpideinstanssi WHERE urakka = "
                                                          @oulun-alueurakan-2005-2010-id ")"))) 1) ;; kokonaismääräästä vähennetään testitoimenpidekoodi, joka ei ole urakassa alunperinkään voimassa
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
    (let [db (:db jarjestelma)
       urakka-id @pudasjarven-alueurakan-id
          maara-kannassa (- (ffirst (q
                                   (str "SELECT count(*)
                                           FROM toimenpidekoodi t4
                                                LEFT JOIN toimenpidekoodi t3 ON t3.id=t4.emo
                                           WHERE t4.taso = 4 AND
                                                t3.id in (SELECT toimenpide FROM toimenpideinstanssi WHERE urakka = "
                                     urakka-id ")"))) 1) ;; kokonaismääräästä vähennetään testitoimenpidekoodi, joka ei ole urakassa alunperinkään voimassa
       response (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat-tasot db urakka-id)]
     (is (not (nil? response)))
     (is (= (count response) maara-kannassa))))

(deftest testaa-tehtavien-voimassaolo
         (let [db (:db jarjestelma)
               urakka-id @oulun-alueurakan-2005-2010-id
               maara-kannassa (- (ffirst (q
                                        (str "SELECT count(*)
                                           FROM toimenpidekoodi t4
                                                LEFT JOIN toimenpidekoodi t3 ON t3.id=t4.emo
                                           WHERE t4.taso = 4 AND
                                                t3.id in (SELECT toimenpide FROM toimenpideinstanssi WHERE urakka = "
                                             @oulun-alueurakan-2005-2010-id ")"))) 1) ;; kokonaismääräästä vähennetään testitoimenpidekoodi, joka ei ole urakassa alunperinkään voimassa
               lahtotilanne (count (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat-tasot db urakka-id))
               _ (u "UPDATE toimenpidekoodi SET voimassaolo_alkuvuosi = 2015, voimassaolo_loppuvuosi = 2025 WHERE nimi = 'Graffitien poisto'")
               tehtava-tulossa (count (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat-tasot db urakka-id))
               _ (u "UPDATE toimenpidekoodi SET voimassaolo_alkuvuosi = 2000, voimassaolo_loppuvuosi = 2004 WHERE nimi = 'Graffitien poisto'")
               tehtava-mennyt (count (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat-tasot db urakka-id))
               _ (u "UPDATE toimenpidekoodi SET voimassaolo_alkuvuosi = 2005, voimassaolo_loppuvuosi = 2007 WHERE nimi = 'Graffitien poisto'")
               tehtava-voimassa (count (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat-tasot db urakka-id))]

              (is (= maara-kannassa lahtotilanne) "Määrittelemättömien tehtävien määrä on odotettu.")
              (is (= maara-kannassa tehtava-voimassa) "Voimassa oleva tehtävä palautuu.")
              (is (= (- maara-kannassa 1) tehtava-tulossa) "Tulossa oleva tehtävä ei palaudu.")
              (is (= (- maara-kannassa 1)  tehtava-mennyt) "Mennyt tehtävä ei palaudu.")))
