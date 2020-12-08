(ns harja.palvelin.palvelut.lampotilat-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.lampotilat :refer :all]
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
                        :hae-teiden-hoitourakoiden-lampotilat (component/using
                                                                (->Lampotilat "ilmatieteenlaitos-urlin-paikka")
                                                               [:http-palvelin :db])
                        :tallenna-teiden-hoitourakoiden-lampotilat (component/using
                                                                (->Lampotilat "ilmatieteenlaitos-urlin-paikka")
                                                                [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))


(use-fixtures :once (compose-fixtures
                      tietokanta-fixture
                      (compose-fixtures jarjestelma-fixture urakkatieto-fixture)))

(deftest hae-teiden-hoitourakoiden-lampotilat-test
  (testing "teiden hoitourakoiden lämpötilojen haku"
    (let [hoitokauden-alkupvm (pvm/->pvm "1.10.2011")
          hoitokauden-loppupvm (pvm/->pvm "30.9.2012")
          hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
          lampotilat (vals (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae-teiden-hoitourakoiden-lampotilat
                                      +kayttaja-jvh+
                                      {:hoitokausi hoitokausi}))
          lampotila-pudussa (first (filter #(= (:nimi %) "Pudasjärven alueurakka 2007-2012") lampotilat))]
      (is (some? (:lampotilaid lampotila-pudussa)) ":lampotilaid")
      (is (some? (:urakka lampotila-pudussa)) "urakka")
      (is (= (:keskilampotila lampotila-pudussa) -8.20M) "keskilampotila")
      (is (= (:pitkakeskilampotila lampotila-pudussa) -9.00M) "pitkakeskilampotila")
      (is (= (:alkupvm lampotila-pudussa) hoitokauden-alkupvm) "hoitokauden-alkupvm")
      (is (= (:loppupvm lampotila-pudussa) hoitokauden-loppupvm) "hoitokauden-loppupvm")))

  (testing "uuden lämpötilan luonti"
    (let [hoitokauden-alkupvm (pvm/->pvm "1.10.2015")
          hoitokauden-loppupvm (pvm/->pvm "30.9.2016")
          hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
          urakka-id @oulun-alueurakan-2014-2019-id

          lampotilat [{:lampotilaid nil :keskilampotila -26.2
                       :pitkakeskilampotila -8.3 :urakka urakka-id
                       :alkupvm hoitokauden-alkupvm :loppupvm hoitokauden-loppupvm}]

          lampotilat-kutsun-jalkeen (vals (kutsu-palvelua (:http-palvelin jarjestelma)
                                                          :tallenna-teiden-hoitourakoiden-lampotilat
                                                          +kayttaja-jvh+
                                                          {:hoitokausi hoitokausi
                                                           :lampotilat lampotilat}))
          lampotila-oulussa (first (filter #(= (:urakka %) urakka-id) lampotilat-kutsun-jalkeen))]
      (is (some? (:lampotilaid lampotila-oulussa)) ":lampotilaid")
      (is (some? (:urakka lampotila-oulussa)) "urakka")
      (is (= (:keskilampotila lampotila-oulussa) -26.20M) "keskilampotila")
      (is (= (:pitkakeskilampotila lampotila-oulussa) -8.30M) "pitkakeskilampotila")
      (is (= (:alkupvm lampotila-oulussa) hoitokauden-alkupvm) "hoitokauden-alkupvm")
      (is (= (:loppupvm lampotila-oulussa) hoitokauden-loppupvm) "hoitokauden-loppupvm")))

  (testing "olemassaolevan lämpötilan päivitys"
    (let [hoitokauden-alkupvm (pvm/->pvm "1.10.2014")
          hoitokauden-loppupvm (pvm/->pvm "30.9.2015")
          hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
          urakka-id @oulun-alueurakan-2014-2019-id
          lampotila-id @oulun-alueurakan-lampotila-hk-2014-2015
          lampotilat [{:lampotilaid lampotila-id :keskilampotila -26.2
                       :pitkakeskilampotila -8.3 :urakka urakka-id
                       :alkupvm hoitokauden-alkupvm :loppupvm hoitokauden-loppupvm}]

          lampotilat-kutsun-jalkeen (vals (kutsu-palvelua (:http-palvelin jarjestelma)
                                                          :tallenna-teiden-hoitourakoiden-lampotilat
                                                          +kayttaja-jvh+
                                                          {:hoitokausi hoitokausi
                                                           :lampotilat lampotilat}))
          lampotila-oulussa (first (filter #(= (:urakka %) urakka-id) lampotilat-kutsun-jalkeen))]
      (is (= (:lampotilaid lampotila-oulussa) lampotila-id) ":lampotilaid")
      (is (some? (:urakka lampotila-oulussa)) "urakka")
      (is (= (:keskilampotila lampotila-oulussa) -26.20M) "keskilampotila")
      (is (= (:pitkakeskilampotila lampotila-oulussa) -8.30M) "pitkakeskilampotila")
      (is (= (:alkupvm lampotila-oulussa) hoitokauden-alkupvm) "hoitokauden-alkupvm")
      (is (= (:loppupvm lampotila-oulussa) hoitokauden-loppupvm) "hoitokauden-loppupvm"))))
