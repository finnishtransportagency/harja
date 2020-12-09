(ns harja.palvelin.palvelut.suolasakot-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.lampotilat :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :lampotilat (component/using
                                      (->Lampotilat "ilmatieteenlaitos-urlin-paikka")
                                      [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      tietokanta-fixture
                      (compose-fixtures jarjestelma-fixture urakkatieto-fixture)))

(deftest suolasakkojen-ja-lampotilojen-haku-ja-tallennus
  (testing "suolasakon haku"
    (let [urakka-id @oulun-alueurakan-2014-2019-id
          tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :hae-urakan-suolasakot-ja-lampotilat
                                     +kayttaja-jvh+
                                     urakka-id)
          suolasakot (:suolasakot tulos)
          lampotilat (:lampotilat tulos)
          hk-2014-2015-lt (first (filter #(= (pvm/->pvm "01.10.2014") (:alkupvm %)) lampotilat))
          suolasakko (first (filter #(= (:hoitokauden_alkuvuosi %) 2014) suolasakot))]
      (is (= (:maksukuukausi suolasakko) 8) "maksukuukausi")
      (is (= (:urakka suolasakko) urakka-id) "urakka")
      (is (> (count lampotilat) 0) "lampotilat saatiin")
      (is (= (:keskilampotila hk-2014-2015-lt) -6.2 ) "lampotila hk-2014-2015-lt")
      (is (= (:pitkakeskilampotila hk-2014-2015-lt) -9.3 ) "lampotila hk-2014-2015-lt")
      (is (= (:loppupvm hk-2014-2015-lt) (pvm/->pvm "30.9.2015") ) "lampotila hk-2014-2015-lt")
      (is (= (:indeksi suolasakko) "MAKU 2005") "indeksi")
      (is (= (:hoitokauden_alkuvuosi suolasakko) 2014) "hoitokauden alkuvuosi")
      (is (= (:maara suolasakko) 30.0))))

  (testing "suolasakon luonti ja päivitys"
    (let [urakka-id @oulun-alueurakan-2014-2019-id
          lisattava-suolasakko {:hoitokauden-alkuvuosi 2014
                                :urakka urakka-id
                                :suolasakko {:maksukuukausi 6
                                             :indeksi "MAKU 2005"
                                             :maara 40
                                             :kaytossa true
                                             :hoitokauden_alkuvuosi 2014
                                             :talvisuolaraja 100}}
          tulos (:suolasakot (kutsu-palvelua
                               (:http-palvelin jarjestelma)
                               :tallenna-suolasakko-ja-pohjavesialueet
                               +kayttaja-jvh+
                               lisattava-suolasakko))
          kutsun-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :hae-urakan-suolasakot-ja-lampotilat
                                         +kayttaja-jvh+
                                         urakka-id)
          suolasakko (first (filter #(= (:hoitokauden_alkuvuosi %) 2014)
                                    (:suolasakot kutsun-jalkeen))) ]

      (is (= (:maksukuukausi suolasakko) 6) "maksukuukausi")
      (is (= (:kaytossa suolasakko) true) "kaytossa")
      (is (= (:talvisuolaraja suolasakko) 100M) "talvisuolaraja")
      (is (= (:urakka suolasakko) urakka-id) "urakka")
      (is (= (:indeksi suolasakko) "MAKU 2005") "indeksi")
      (is (= (:hoitokauden_alkuvuosi suolasakko) 2014) "hoitokauden alkuvuosi")
      (is (= (:maara suolasakko) 40.0)))))
