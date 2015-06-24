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


;;tallenna-muiden-toiden-toteuma:  {:hinnoittelu :yksikkohinta, :suorittajan {:nimi "on"}, :suorittajan-nimi "on",
;; :suorittajan-ytunnus nil, :urakka-id 1, :yksikko "tiekm", :urakan-loppupvm #inst "2010-09-29T21:00:00.000-00:00",
;; :alkanut #inst "2006-02-01T22:00:00.000-00:00", :urakan-alkupvm #inst "2005-09-30T21:00:00.000-00:00",
;; :tehtava {:paivanhinta nil, :maara 2, :toimenpidekoodi 1368}, :hoitokausi-aloituspvm #inst "2005-09-30T21:00:00.000-00:00",
;; :paattynyt #inst "2006-02-01T22:00:00.000-00:00", :hoitokausi-lopetuspvm #inst "2006-09-29T21:00:00.000-00:00", :yksikkohinta 31,
;; :toimenpideinstanssi {:t3_nimi "Laaja toimenpide",
;; :t3_emo 907, :t1_koodi "23000", :t3_koodi "23104", :t2_nimi "Talvihoito", :t2_emo 906, :t1_nimi "Hoito, tie",
;; :tpi_nimi "Oulu Talvihoito TP", :id 911, :t2_koodi "23100", :tpi_id 1}, :sopimus [1 "1H05228/01"], :sopimus-id 1,
;; :tyyppi :muutostyo, :uusi-muutoshintainen-tyo 1368}

#_(deftest tallenna-muut-tyot-toteuma-testi
  (let [tyon-pvm (java.sql.Date. 105 9 1) ;;1.10.2005
        toteuman-pvm (java.sql.Date. 105 11 12)
        toteuman-lisatieto "Testikeissin lisätieto2"
        tyo {:urakka-id @oulun-alueurakan-id :sopimus-id @oulun-alueurakan-paasopimuksen-id
             :alkanut tyon-pvm :paattynyt tyon-pvm
             :suorittajan-nimi "Alihankkijapaja Ky" :suorittajan-ytunnus "123456-Y"
             :tyyppi :muutostyo
             :lisatieto toteuman-lisatieto
             :tehtava {:paivanhinta 456, :maara 2, :toimenpidekoodi 1368}}
        ;select * from toteuma where tyyppi in ('muutostyo', 'lisatyo', 'akillinen-hoitotyo');
;        select * from toteuma_tehtava tt where tt.toteuma in (SELECT id from toteuma where tyyppi in ('muutostyo', 'lisatyo', 'akillinen-hoitotyo')) and poistettu is NOT  true;

        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*)
                                                       FROM toteuma
                                                      WHERE urakka = " @oulun-alueurakan-id "
                                                      AND tyyppi IN ('muutostyo') AND alkanut = '" tyon-pvm "';")))
        res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :tallenna-muiden-toiden-toteuma +kayttaja-jvh+ tyo)
        lisatty (first (filter #(and
                                 (= (:alkanut %) tyon-pvm)
                                 (= (:lisatieto %) toteuman-lisatieto)) res))]
    (is (= (:alkanut lisatty) tyon-pvm) "Tallennetun muun työn alkanut pvm")
    (is (= (:lisatieto lisatty) toteuman-lisatieto) "Tallennetun erilliskustannuksen lisätieto")
    (is (= (count res) (+ 1 maara-ennen-lisaysta)) "Tallennuksen jälkeen muiden töiden määrä")
    (u
      (str "DELETE FROM toteuma
                    WHERE pvm = '" tyon-pvm "' AND lisatieto = '" toteuman-lisatieto "'"))))