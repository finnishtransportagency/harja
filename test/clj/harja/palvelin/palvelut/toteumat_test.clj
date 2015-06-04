(ns harja.palvelin.palvelut.toteumat-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toteumat :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (apply tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :urakan-erilliskustannukset (component/using
                                   (->Toteumat)
                                   [:http-palvelin :db])
          :tallenna-erilliskustannus (component/using
                                           (->Toteumat)
                                           [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; käyttää testidata.sql:stä tietoa
(deftest erilliskustannukset-haettu-oikein
         (let [alkupvm (java.sql.Date. 105 9 1)
               loppupvm (java.sql.Date. 106 10 30)
               res (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :urakan-erilliskustannukset +kayttaja-jvh+
                                     {:urakka-id @oulun-alueurakan-id
                                      :alkupvm alkupvm
                                      :loppupvm loppupvm})
               oulun-alueurakan-toiden-lkm (ffirst (q
                                                     (str "SELECT count(*)
                                                       FROM erilliskustannus
                                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @oulun-alueurakan-id
               ") AND pvm >= '2005-10-01' AND pvm <= '2006-09-30'")))]
           (is (= (count res) oulun-alueurakan-toiden-lkm) "Erilliskustannusten määrä")))




(deftest tallenna-erilliskustannus-testi
         (let [hoitokauden-alkupvm (java.sql.Date. 105 9 1) ;;1.10.2005
               hoitokauden-loppupvm (java.sql.Date. 106 10 30) ;;30.9.2006
               toteuman-pvm (java.sql.Date. 105 11 12)
               toteuman-lisatieto "Testikeissin lisätieto"
               ek {:urakka-id @oulun-alueurakan-id
                   :alkupvm hoitokauden-alkupvm
                   :loppupvm hoitokauden-loppupvm
                   :pvm toteuman-pvm :rahasumma 20000.0
                   :indeksin_nimi "MAKU 2005" :toimenpideinstanssi 1 :sopimus 1
                   :tyyppi "asiakastyytyvaisyysbonus" :lisatieto toteuman-lisatieto}
               maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*)
                                                       FROM erilliskustannus
                                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @oulun-alueurakan-id
                                         ") AND pvm >= '2005-10-01' AND pvm <= '2006-09-30'")))
               res (kutsu-palvelua (:http-palvelin jarjestelma)
                     :tallenna-erilliskustannus +kayttaja-jvh+ ek)
               lisatty (first (filter #(and
                                  (= (:pvm %) toteuman-pvm)
                                  (= (:lisatieto %) toteuman-lisatieto)) res))]
           (is (= (:pvm lisatty) toteuman-pvm) "Tallennetun erilliskustannuksen pvm")
           (is (= (:lisatieto lisatty) toteuman-lisatieto) "Tallennetun erilliskustannuksen lisätieto")
           (is (= (:indeksin_nimi lisatty) "MAKU 2005") "Tallennetun erilliskustannuksen indeksin nimi")
           (is (= (:rahasumma lisatty) 20000.0) "Tallennetun erilliskustannuksen pvm")
           (is (= (:toimenpideinstanssi lisatty) 1) "Tallennetun erilliskustannuksen tp")
           (is (= (count res) (+ 1 maara-ennen-lisaysta)) "Tallennuksen jälkeen erilliskustannusten määrä")
           (u
             (str "DELETE FROM erilliskustannus
                    WHERE pvm = '2005-12-12' AND lisatieto = '" toteuman-lisatieto "'"))))