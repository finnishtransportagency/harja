(ns harja.palvelin.palvelut.suolasakot_test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.lampotilat :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-urakan-suolasakot-ja-lampotilat (component/using
                                                               (->Lampotilat "ilmatieteenlaitos-urlin-paikka")
                                                               [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-urakan-suolasakot-ja-lampotilat-test
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        suolasakko (first (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-urakan-suolasakot-ja-lampotilat
                                          +kayttaja-jvh+
                                          urakka-id))
        hoitokauden-alkupvm (java.sql.Date. 114 9 1)        ;;1.10.2005
        hoitokauden-loppupvm (java.sql.Date. 115 8 30)      ;;30.9.2006
        ]
    (is (= (:maksukuukausi suolasakko) 8) "maksukuukausi")
    (is (= (:urakka suolasakko) urakka-id) "urakka")
    (is (= (:keskilampotila suolasakko) -6.2) "keskilampotila")
    (is (= (:pitkakeskilampotila suolasakko) -9.0) "pitkakeskilampotila")
    (is (= (:lt_alkupvm suolasakko) hoitokauden-alkupvm) "hoitokauden-alkupvm")
    (is (= (:lt_loppupvm suolasakko) hoitokauden-loppupvm) "hoitokauden-loppupvm")
    (is (= (:indeksi suolasakko) "MAKU 2010") "indeksi")
    (is (= (:hoitokauden_alkuvuosi suolasakko) 2014) "hoitokauden alkuvuosi")
    (is (= (:maara suolasakko) 30.0))
    (is (= (:maksukuukausi suolasakko) 8))))