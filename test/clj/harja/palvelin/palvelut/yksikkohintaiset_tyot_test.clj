(ns harja.palvelin.palvelut.yksikkohintaiset-tyot-test
  (:require [clojure.test :refer :all]

            [harja.kyselyt.urakat :as urk-q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yksikkohintaiset-tyot :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]))


(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start 
                     (component/system-map
                      :db (tietokanta/luo-tietokanta testitietokanta)
                      :http-palvelin (testi-http-palvelin)
                      :yksikkohintaiset-tyot (component/using
                                  (->Yksikkohintaiset-tyot)
                                  [:http-palvelin :db])))))
  
  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; käyttää testidata.sql:stä tietoa
(deftest kaikki-yksikkohintaiset-tyot-haettu-oikein 
  (let [yksikkohintaiset-tyot (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :yksikkohintaiset-tyot (oulun-2005-urakan-tilaajan-urakanvalvoja)
                                @oulun-alueurakan-2005-2010-id)
        oulun-alueurakan-toiden-lkm (ffirst (q 
                                             (str "SELECT count(*)
                                                       FROM yksikkohintainen_tyo
                                                      WHERE urakka = " @oulun-alueurakan-2005-2010-id)))]
    (is (= (count yksikkohintaiset-tyot) oulun-alueurakan-toiden-lkm))))


(deftest tallenna-yksikkohintaiset-tyot
  (let [tyot [{:yhteensa-kkt-1-9 122040, :yhteensa 162720, :yhteensa-kkt-10-12 40680,
                      :loppupvm (pvm/->pvm "31.12.2014") :yksikko "ha", :koskematon true,
                      :tehtava 1432, :urakka 4, :yksikkohinta 678, :maara 600,
                      :tehtavan_nimi "Metsän harvennus", :sopimus 2, :maara-kkt-1-9 180,
                      :alkupvm (pvm/->pvm "1.10.2014") :tehtavan_id 1432}
                     {:yhteensa-kkt-1-9 122040, :yhteensa 162720, :yhteensa-kkt-10-12 40680,
                      :loppupvm (pvm/->pvm "30.09.2015") :yksikko "ha", :koskematon true,
                      :tehtava 1432, :urakka 4, :yksikkohinta 678, :maara 1800,
                      :tehtavan_nimi "Metsän harvennus", :sopimus 2, :maara-kkt-1-9 180,
                      :alkupvm (pvm/->pvm "1.1.2015") :tehtavan_id 1432}]
        yksikkohintaiset-tyot-ennen (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :yksikkohintaiset-tyot +kayttaja-jvh+
                                              @oulun-alueurakan-2014-2019-id)
        paivitys (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-urakan-yksikkohintaiset-tyot +kayttaja-jvh+
                                 {:urakka-id @oulun-alueurakan-2014-2019-id
                                  :sopimusnumero @oulun-alueurakan-2014-2019-paasopimuksen-id
                                  :tyot tyot})
        yksikkohintaiset-tyot-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                      :yksikkohintaiset-tyot +kayttaja-jvh+
                                                      @oulun-alueurakan-2014-2019-id)
        metsan-harvennus-ennen-alku (first (filter #(and (= (:alkupvm %) (pvm/->pvm "1.10.2014"))
                                                         (= (:tehtavan_nimi %) "Metsän harvennus")) yksikkohintaiset-tyot-ennen))
        metsan-harvennus-ennen-loppu (first (filter #(and (= (:alkupvm %) (pvm/->pvm "1.1.2015"))
                                                          (= (:tehtavan_nimi %) "Metsän harvennus")) yksikkohintaiset-tyot-ennen))
        metsan-harvennus-jalkeen-alku (first (filter #(and (= (:alkupvm %) (pvm/->pvm "1.10.2014"))
                                                         (= (:tehtavan_nimi %) "Metsän harvennus")) yksikkohintaiset-tyot-jalkeen))
        metsan-harvennus-jalkeen-loppu (first (filter #(and (= (:alkupvm %) (pvm/->pvm "1.1.2015"))
                                                          (= (:tehtavan_nimi %) "Metsän harvennus")) yksikkohintaiset-tyot-jalkeen))
        paivityksen-jalkeen-alku (first (filter #(and (= (:alkupvm %) (pvm/->pvm "1.10.2014"))
                                                           (= (:tehtavan_nimi %) "Metsän harvennus")) paivitys))
        paivityksen-jalkeen-loppu (first (filter #(and (= (:alkupvm %) (pvm/->pvm "1.1.2015"))
                                                            (= (:tehtavan_nimi %) "Metsän harvennus")) paivitys))]
    (is (= 60.0 (:maara metsan-harvennus-ennen-alku)))
    (is (= 180.0 (:maara metsan-harvennus-ennen-loppu)))
    (is (= 600.0 (:maara metsan-harvennus-jalkeen-alku) (:maara paivityksen-jalkeen-alku)))
    (is (= 1800.0 (:maara metsan-harvennus-jalkeen-loppu) (:maara paivityksen-jalkeen-loppu)))
    (is (= 100.0 (:yksikkohinta metsan-harvennus-ennen-alku)))
    (is (= 100.0 (:yksikkohinta metsan-harvennus-ennen-loppu)))
    (is (= 678.0 (:yksikkohinta metsan-harvennus-jalkeen-alku) (:yksikkohinta paivityksen-jalkeen-alku)))
    (is (= 678.0 (:yksikkohinta metsan-harvennus-jalkeen-loppu) (:yksikkohinta paivityksen-jalkeen-loppu)))
    (is (= (count metsan-harvennus-ennen-loppu) (count paivityksen-jalkeen-loppu) (count metsan-harvennus-jalkeen-loppu)) "sama määrä eri yks.hint. palveluiden paluuarvioista")))


