(ns harja.palvelin.palvelut.yllapito_toteumat-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapito_toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae-yllapito-toteumat (component/using
                                   (->YllapitoToteumat)
                                   [:http-palvelin :db])
          :hae-yllapito-toteuma (component/using
                                  (->YllapitoToteumat)
                                  [:http-palvelin :db])
          :hae-laskentakohteet (component/using
                                 (->YllapitoToteumat)
                                 [:http-palvelin :db])
          :tallenna-yllapito-toteuma (component/using
                                       (->YllapitoToteumat)
                                       [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; käyttää testidata.sql:stä tietoa
(deftest yllapitototeumien-haku-test
         (let [urakka (hae-oulun-tiemerkintaurakan-id)
               sopimus (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
               alkupvm (pvm/luo-pvm 2016 0 1)
               loppupvm (pvm/luo-pvm 2016 11 31)
               res (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :hae-yllapito-toteumat +kayttaja-jvh+
                                   {:urakka urakka
                                    :sopimus sopimus
                                    :alkupvm alkupvm
                                    :loppupvm loppupvm})
               oulun-tiemerkintaurakan-toiden-lkm (ffirst (q
                                                     (str "SELECT count(*)
                                                       FROM yllapito_toteuma
                                                     WHERE urakka = " urakka
                                                          " AND sopimus = " sopimus
                                                          " AND pvm >= '2016-1-01' AND pvm <= '2016-12-31'")))]
           (is (= (count res) oulun-tiemerkintaurakan-toiden-lkm) "Muiden töiden määrä")))

(deftest tallenna-yllapito-toteuma-test
         (let [urakka (hae-oulun-tiemerkintaurakan-id)
               sopimus (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
               alkupvm (pvm/luo-pvm 2016 0 1)
               loppupvm (pvm/luo-pvm 2016 11 31)
               toteuman-pvm (pvm/luo-pvm 2016 11 24)
               hyotykuorma {:pvm toteuman-pvm
                            :selite "Jouluaattona hommissa"
                            :laskentakohde [nil "ei kohdetta tässä.."]
                            :uusi-laskentakohde "Uusikohde"
                            :urakka urakka
                            :sopimus sopimus
                            :yllapitoluokka 1
                            :alkupvm alkupvm
                            :loppupvm loppupvm
                            :hinta 665.5}
               maara-ennen-lisaysta (ffirst (q
                                              (str "SELECT count(*)
                                                       FROM yllapito_toteuma
                                                     WHERE urakka = " urakka
                                                   " AND sopimus = " sopimus
                                                   " AND pvm >= '2016-1-01' AND pvm <= '2016-12-31'")))
               laskentakohdelkm-ennen (ffirst (q
                                                (str "SELECT count(*)
                                                       FROM urakka_laskentakohde
                                                     WHERE urakka = " urakka)))
               res (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :tallenna-yllapito-toteuma +kayttaja-jvh+
                                   hyotykuorma)
               maara-lisayksen-jalkeen (ffirst (q
                                              (str "SELECT count(*)
                                                       FROM yllapito_toteuma
                                                     WHERE urakka = " urakka
                                                   " AND sopimus = " sopimus
                                                   " AND pvm >= '2016-1-01' AND pvm <= '2016-12-31'")))
               lisatty-toteuma (first (filter #(= (:pvm %) toteuman-pvm) (:toteumat res)))
               laskentakohteet-jalkeen (:laskentakohteet res)]
           (is (= (+ 1 maara-ennen-lisaysta) maara-lisayksen-jalkeen))
           (is (= (:pvm lisatty-toteuma) toteuman-pvm) "Tallennetun yllapitototeuman pvm")
           (is (= (:hinta lisatty-toteuma) 665.5M) "Tallennetun yllapitototeuman pvm")
           (is (= (count (:toteumat res)) maara-lisayksen-jalkeen (+ 1 maara-ennen-lisaysta)) "Tallennuksen jälkeen erilliskustannusten määrä")
           (is (= (count laskentakohteet-jalkeen) (+ 1 laskentakohdelkm-ennen)))))