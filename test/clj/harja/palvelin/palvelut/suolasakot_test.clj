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
                      tietokanta-fixture
                      (compose-fixtures jarjestelma-fixture urakkatieto-fixture)))

(deftest suolasakkojen-ja-lampotilojen-haku-ja-tallennus
  (testing "suolasakon haku"
    (let [urakka-id @oulun-alueurakan-2014-2019-id
         suolasakot (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-urakan-suolasakot-ja-lampotilat
                                    +kayttaja-jvh+
                                    urakka-id)
         suolasakko (first (filter #(= (:hoitokauden_alkuvuosi %) 2014) suolasakot))
         hoitokauden-alkupvm (java.sql.Date. 114 9 1)       ;;1.10.2005
         hoitokauden-loppupvm (java.sql.Date. 115 8 30)]    ;;30.9.2006
     (is (= (:maksukuukausi suolasakko) 8) "maksukuukausi")
     (is (= (:urakka suolasakko) urakka-id) "urakka")
     (is (= (:keskilampotila suolasakko) -6.2) "keskilampotila")
     (is (= (:pitkakeskilampotila suolasakko) -9.0) "pitkakeskilampotila")
     (is (= (:lt_alkupvm suolasakko) hoitokauden-alkupvm) "hoitokauden-alkupvm")
     (is (= (:lt_loppupvm suolasakko) hoitokauden-loppupvm) "hoitokauden-loppupvm")
     (is (= (:indeksi suolasakko) "MAKU 2010") "indeksi")
     (is (= (:hoitokauden_alkuvuosi suolasakko) 2014) "hoitokauden alkuvuosi")
     (is (= (:maara suolasakko) 30.0))))

(testing "suolasakon luonti ja päivitys"
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        hoitokauden-alkupvm (java.sql.Date. 115 9 1)        ;;1.10.2015
        hoitokauden-loppupvm (java.sql.Date. 116 8 30)      ;;30.9.2016
        hoitokauden-alkupvm-2014 (java.sql.Date. 114 9 1)
        hoitokauden-loppupvm-2014 (java.sql.Date. 115 8 30)

        lisattava-suolasakko {:maksukuukausi 6 :indeksi "MAKU 2005" :maara 40 :keskilampotila -16
                                  :hoitokauden_alkuvuosi 2015
                                  :muokattu true :pitkakeskilampotila -8 :urakka urakka-id
                                  :lt_alkupvm hoitokauden-alkupvm :lt_loppupvm hoitokauden-loppupvm}
        kannassaolevat-suolasakot  (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :hae-urakan-suolasakot-ja-lampotilat
                                   +kayttaja-jvh+
                                   urakka-id)
        paivitettava-suolasakko (first (filter #(= (:hoitokauden_alkuvuosi %) 2014) kannassaolevat-suolasakot))
        paivittava-suolasakko {:id (:id                  paivitettava-suolasakko) :maksukuukausi 5
                               :indeksi "MAKU 2010" :maara 31 :keskilampotila -11 :hoitokauden_alkuvuosi 2014
                               :muokattu              true :pitkakeskilampotila -12 :urakka urakka-id
                               :lt_id                                               (:lt_id                 paivitettava-suolasakko)
                               :lt_alkupvm            hoitokauden-alkupvm-2014 :lt_loppupvm hoitokauden-loppupvm-2014}
        urakan-suolasakot-kutsun-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                         :tallenna-suolasakko-ja-lampotilat
                                                         +kayttaja-jvh+
                                                         lisattava-suolasakko)
        urakan-suolasakot-kutsun-jalkeen2 (kutsu-palvelua (:http-palvelin jarjestelma)
                                                         :tallenna-suolasakko-ja-lampotilat
                                                         +kayttaja-jvh+
                                                         paivittava-suolasakko)
        suolasakko (first (filter #(= (:hoitokauden_alkuvuosi %) 2015) urakan-suolasakot-kutsun-jalkeen))
        paivitetty-suolasakko (first (filter #(= (:hoitokauden_alkuvuosi %) 2014) urakan-suolasakot-kutsun-jalkeen2))]
    (is (= (:maksukuukausi suolasakko) 6) "maksukuukausi")
    (is (= (:urakka suolasakko) urakka-id) "urakka")
    (is (= (:keskilampotila suolasakko) -16.0) "keskilampotila")
    (is (= (:pitkakeskilampotila suolasakko) -8.0) "pitkakeskilampotila")
    (is (= (:lt_alkupvm suolasakko) hoitokauden-alkupvm) "hoitokauden-alkupvm")
    (is (= (:lt_loppupvm suolasakko) hoitokauden-loppupvm) "hoitokauden-loppupvm")
    (is (= (:indeksi suolasakko) "MAKU 2005") "indeksi")
    (is (= (:hoitokauden_alkuvuosi suolasakko) 2015) "hoitokauden alkuvuosi")
    (is (= (:maara suolasakko) 40.0))

    (is (= (:maksukuukausi paivitetty-suolasakko) 5) "maksukuukausi")
    (is (= (:urakka paivitetty-suolasakko) urakka-id) "urakka")
    (is (= (:keskilampotila paivitetty-suolasakko) -11.0) "keskilampotila")
    (is (= (:pitkakeskilampotila paivitetty-suolasakko) -12.0) "pitkakeskilampotila")
    (is (= (:lt_alkupvm paivitetty-suolasakko) hoitokauden-alkupvm-2014) "hoitokauden-alkupvm")
    (is (= (:lt_loppupvm paivitetty-suolasakko) hoitokauden-loppupvm-2014) "hoitokauden-loppupvm")
    (is (= (:indeksi paivitetty-suolasakko) "MAKU 2010") "indeksi")
    (is (= (:hoitokauden_alkuvuosi paivitetty-suolasakko) 2014) "hoitokauden alkuvuosi")
    (is (= (:maara paivitetty-suolasakko) 31.0)))))
