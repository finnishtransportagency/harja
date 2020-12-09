(ns harja.palvelin.palvelut.urakan-toimenpiteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakan-toimenpiteet :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :urakan-toimenpiteet-ja-tehtavat (component/using
                                                           (->Urakan-toimenpiteet)
                                                           [:http-palvelin :db])
                        :urakan-toimenpiteet-ja-tehtavat (component/using
                                                           (->Urakan-toimenpiteet)
                                                           [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-urakan-1-toimenpiteet
  (let [urakka-id @oulun-alueurakan-2005-2010-id
        response (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :urakan-toimenpiteet +kayttaja-jvh+ urakka-id)
        tpi-maara (ffirst (q
                            (str "SELECT count(*)
                                                       FROM toimenpideinstanssi
                                                      WHERE urakka = " @oulun-alueurakan-2005-2010-id ";")))
        tpit (into #{}
                   (map :tpi_nimi response))]
    (is (not (nil? response)))
    (is (= (count response) tpi-maara))
    (is (= (:t3_nimi (first response)) "Talvihoito laaja TPI"))
    (is (= (:id (first (sort-by :tpi_id response))) 618))
    (is (contains? tpit "Oulu Talvihoito TP"))
    (is (contains? tpit "Oulu Liikenneympäristön hoito TP"))
    (is (contains? tpit "Oulu Sorateiden hoito TP"))))


(deftest hae-urakan-1-toimenpiteet-ja-tehtavat
  (let [urakka-id @oulun-alueurakan-2005-2010-id
        response (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :urakan-toimenpiteet-ja-tehtavat  +kayttaja-jvh+ urakka-id)]
    (is (not (nil? response)))))
