(ns harja.palvelin.palvelut.laskutusyhteenveto-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto :as laskutusyhteenveto]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      tietokanta-fixture
                      (compose-fixtures jarjestelma-fixture urakkatieto-fixture)))

(deftest laskutusyhteenvedon-tietojen-haku
  (testing "laskutusyhteenvedon-tietojen-haku"
    (let [haetut-tiedot (laskutusyhteenveto/hae-laskutusyhteenvedon-tiedot
                          (:db jarjestelma)
                          +kayttaja-jvh+
                          {:urakka-id          @oulun-alueurakan-2014-2019-id
                           :hk-alkupvm         (java.sql.Date. 114 9 1)
                           :hk-loppupvm        (java.sql.Date. 115 8 30)
                           :aikavali-alkupvm   (java.sql.Date. 115 7 1)
                            :aikavali-loppupvm (java.sql.Date. 115 7 30)})
          haetut-tiedot-talvihoito (first (filter #(= (:tuotekoodi %) "23100") haetut-tiedot))
          haetut-tiedot-liikenneymparisto (first (filter #(= (:tuotekoodi %) "23110") haetut-tiedot))
          haetut-tiedot-soratiet (first (filter #(= (:tuotekoodi %) "23120") haetut-tiedot))
          _ (log/debug "haetut-tiedot-talvihoito" haetut-tiedot-talvihoito)
          _ (log/debug "haetut-tiedot-liikenneymparisto" haetut-tiedot-liikenneymparisto)
          _ (log/debug "haetut-tiedot-soratiet" haetut-tiedot-soratiet)

          odotetut-talvihoito {:akilliset_hoitotyot_laskutetaan                 0.0M
                               :akilliset_hoitotyot_laskutetaan_ind_korotettuna 0.0M
                               :akilliset_hoitotyot_laskutetaan_ind_korotus     0.0M
                               :akilliset_hoitotyot_laskutettu                  0.0M
                               :akilliset_hoitotyot_laskutettu_ind_korotettuna  0.0M
                               :akilliset_hoitotyot_laskutettu_ind_korotus      0.0M
                               :bonukset_laskutetaan                            1000.0M
                               :bonukset_laskutetaan_ind_korotettuna            1004.06958187041174593000M
                               :bonukset_laskutetaan_ind_korotus                4.06958187041174593000M
                               :bonukset_laskutettu                             0.0M
                               :bonukset_laskutettu_ind_korotettuna             0.0M
                               :bonukset_laskutettu_ind_korotus                 0.0M
                               :erilliskustannukset_laskutetaan                 1000.0M
                               :erilliskustannukset_laskutetaan_ind_korotettuna 1016.9166932652410000M
                               :erilliskustannukset_laskutetaan_ind_korotus     16.9166932652410000M
                               :erilliskustannukset_laskutettu                  1000.0M
                               :erilliskustannukset_laskutettu_ind_korotettuna  990.42451324609000255000M
                               :erilliskustannukset_laskutettu_ind_korotus      -9.57548675390999745000M
                               :kaikki_laskutetaan                              8985.791394829237321930000M
                               :kaikki_laskutetaan_ind_korotus                  205.791394829237321930000M
                               :kaikki_laskutettu                               39239.24672837535932680200M
                               :kaikki_laskutettu_ind_korotus                   129.24672837535932680200M
                               :kaikki_paitsi_kht_laskutetaan                   5485.791394829237321930000M
                               :kaikki_paitsi_kht_laskutetaan_ind_korotus       146.582968400893821930000M
                               :kaikki_paitsi_kht_laskutettu                    4229.24672837535932680200M
                               :kaikki_paitsi_kht_laskutettu_ind_korotus        12.89498882859880851200M
                               :kht_laskutetaan                                 3500.0M
                               :kht_laskutetaan_ind_korotettuna                 3559.2084264283435000M
                               :kht_laskutetaan_ind_korotus                     59.2084264283435000M
                               :kht_laskutettu                                  35010.0M
                               :kht_laskutettu_ind_korotettuna                  35126.35173954676051829000M
                               :kht_laskutettu_ind_korotus                      116.35173954676051829000M
                               :muutostyot_laskutetaan                          1000.0M
                               :muutostyot_laskutetaan_ind_korotettuna          1016.91669326524100000M
                               :muutostyot_laskutetaan_ind_korotus              16.91669326524100000M
                               :muutostyot_laskutettu                           1000.0M
                               :muutostyot_laskutettu_ind_korotettuna           1014.04404723906800000M
                               :muutostyot_laskutettu_ind_korotus               14.04404723906800000M
                               :nimi                                            "Talvihoito"
                               :sakot_laskutetaan                               0.0M
                               :sakot_laskutetaan_ind_korotettuna               0.0M
                               :sakot_laskutetaan_ind_korotus                   0.0M
                               :sakot_laskutettu                                100.0M
                               :sakot_laskutettu_ind_korotettuna                99.48930737312480054200M
                               :sakot_laskutettu_ind_korotus                    -0.51069262687519945800M
                               :suolasakot_laskutetaan                          2280.00000M
                               :suolasakot_laskutetaan_ind_korotettuna          2388.680000000000076000000M
                               :suolasakot_laskutetaan_ind_korotus              108.680000000000076000000M
                               :suolasakot_laskutettu                           0.0M
                               :suolasakot_laskutettu_ind_korotettuna           0.0M
                               :suolasakot_laskutettu_ind_korotus               0.0M
                               :tpi                                             4
                               :tuotekoodi                                      "23100"
                               :yht_laskutetaan                                 0.0M
                               :yht_laskutetaan_ind_korotettuna                 0.0M
                               :yht_laskutetaan_ind_korotus                     0.0M
                               :yht_laskutettu                                  2000.0M
                               :yht_laskutettu_ind_korotettuna                  2008.93712097031600542000M
                               :yht_laskutettu_ind_korotus                      8.93712097031600542000M}

          odotetut-liikenneymparisto {:akilliset_hoitotyot_laskutetaan                 2000.0M
                                      :akilliset_hoitotyot_laskutetaan_ind_korotettuna 2016.91669326524100000M
                                      :akilliset_hoitotyot_laskutetaan_ind_korotus     16.91669326524100000M
                                      :akilliset_hoitotyot_laskutettu                  1000.0M
                                      :akilliset_hoitotyot_laskutettu_ind_korotettuna  1014.04404723906800000M
                                      :akilliset_hoitotyot_laskutettu_ind_korotus      14.04404723906800000M
                                      :bonukset_laskutetaan                            0.0M
                                      :bonukset_laskutetaan_ind_korotettuna            0.0M
                                      :bonukset_laskutetaan_ind_korotus                0.0M
                                      :bonukset_laskutettu                             0.0M
                                      :bonukset_laskutettu_ind_korotettuna             0.0M
                                      :bonukset_laskutettu_ind_korotus                 0.0M
                                      :erilliskustannukset_laskutetaan                 0.0M
                                      :erilliskustannukset_laskutetaan_ind_korotettuna 0.0M
                                      :erilliskustannukset_laskutetaan_ind_korotus     0.0M
                                      :erilliskustannukset_laskutettu                  0.0M
                                      :erilliskustannukset_laskutettu_ind_korotettuna  0.0M
                                      :erilliskustannukset_laskutettu_ind_korotus      0.0M
                                      :kaikki_laskutetaan                              13101.50015959144600000M
                                      :kaikki_laskutetaan_ind_korotus                  101.50015959144600000M
                                      :kaikki_laskutettu                               6226.65177146504950542000M
                                      :kaikki_laskutettu_ind_korotus                   26.65177146504950542000M
                                      :kaikki_paitsi_kht_laskutetaan                   13101.50015959144600000M
                                      :kaikki_paitsi_kht_laskutetaan_ind_korotus       101.50015959144600000M
                                      :kaikki_paitsi_kht_laskutettu                    6226.65177146504950542000M
                                      :kaikki_paitsi_kht_laskutettu_ind_korotus        26.65177146504950542000M
                                      :kht_laskutetaan                                 0.0M
                                      :kht_laskutetaan_ind_korotettuna                 0.0M
                                      :kht_laskutetaan_ind_korotus                     0.0M
                                      :kht_laskutettu                                  0.0M
                                      :kht_laskutettu_ind_korotettuna                  0.0M
                                      :kht_laskutettu_ind_korotus                      0.0M
                                      :muutostyot_laskutetaan                          8000.0M
                                      :muutostyot_laskutetaan_ind_korotettuna          8033.83338653048200000M
                                      :muutostyot_laskutetaan_ind_korotus              33.83338653048200000M
                                      :muutostyot_laskutettu                           3000.0M
                                      :muutostyot_laskutettu_ind_korotettuna           3014.04404723906800000M
                                      :muutostyot_laskutettu_ind_korotus               14.04404723906800000M
                                      :nimi                                            "Liikenneympäristön hoito"
                                      :sakot_laskutetaan                               0.0M
                                      :sakot_laskutetaan_ind_korotettuna               0.0M
                                      :sakot_laskutetaan_ind_korotus                   0.0M
                                      :sakot_laskutettu                                1200.0M
                                      :sakot_laskutettu_ind_korotettuna                1203.6706032556655000M
                                      :sakot_laskutettu_ind_korotus                    3.6706032556655000M
                                      :suolasakot_laskutetaan                          0.0M
                                      :suolasakot_laskutetaan_ind_korotettuna          0.0M
                                      :suolasakot_laskutetaan_ind_korotus              0.0M
                                      :suolasakot_laskutettu                           0.0M
                                      :suolasakot_laskutettu_ind_korotettuna           0.0M
                                      :suolasakot_laskutettu_ind_korotus               0.0M
                                      :tpi                                             5
                                      :tuotekoodi                                      "23110"
                                      :yht_laskutetaan                                 3000.0M
                                      :yht_laskutetaan_ind_korotettuna                 3050.7500797957230000M
                                      :yht_laskutetaan_ind_korotus                     50.7500797957230000M
                                      :yht_laskutettu                                  1000.0M
                                      :yht_laskutettu_ind_korotettuna                  994.89307373124800542000M
                                      :yht_laskutettu_ind_korotus                      -5.10692626875199458000M}

          odotetut-soratiet {:akilliset_hoitotyot_laskutetaan                 0.0M
                             :akilliset_hoitotyot_laskutetaan_ind_korotettuna 0.0M
                             :akilliset_hoitotyot_laskutetaan_ind_korotus     0.0M
                             :akilliset_hoitotyot_laskutettu                  0.0M
                             :akilliset_hoitotyot_laskutettu_ind_korotettuna  0.0M
                             :akilliset_hoitotyot_laskutettu_ind_korotus      0.0M
                             :bonukset_laskutetaan                            0.0M
                             :bonukset_laskutetaan_ind_korotettuna            0.0M
                             :bonukset_laskutetaan_ind_korotus                0.0M
                             :bonukset_laskutettu                             0.0M
                             :bonukset_laskutettu_ind_korotettuna             0.0M
                             :bonukset_laskutettu_ind_korotus                 0.0M
                             :erilliskustannukset_laskutetaan                 0.0M
                             :erilliskustannukset_laskutetaan_ind_korotettuna 0.0M
                             :erilliskustannukset_laskutetaan_ind_korotus     0.0M
                             :erilliskustannukset_laskutettu                  0.0M
                             :erilliskustannukset_laskutettu_ind_korotettuna  0.0M
                             :erilliskustannukset_laskutettu_ind_korotus      0.0M
                             :kaikki_laskutetaan                              11999.6169805298438000M
                             :kaikki_laskutetaan_ind_korotus                  199.6169805298438000M
                             :kaikki_laskutettu                               100331.95020746888030940000M
                             :kaikki_laskutettu_ind_korotus                   331.95020746888030940000M
                             :kaikki_paitsi_kht_laskutetaan                   1999.6169805298438000M
                             :kaikki_paitsi_kht_laskutetaan_ind_korotus       30.4500478774338000M
                             :kaikki_paitsi_kht_laskutettu                    331.95020746888030940000M
                             :kaikki_paitsi_kht_laskutettu_ind_korotus        0.0M
                             :kht_laskutetaan                                 10000.0M
                             :kht_laskutetaan_ind_korotettuna                 10169.1669326524100000M
                             :kht_laskutetaan_ind_korotus                     169.1669326524100000M
                             :kht_laskutettu                                  100000.0M
                             :kht_laskutettu_ind_korotettuna                  100331.95020746888030940000M
                             :kht_laskutettu_ind_korotus                      331.95020746888030940000M
                             :muutostyot_laskutetaan                          0.0M
                             :muutostyot_laskutetaan_ind_korotettuna          0.0M
                             :muutostyot_laskutetaan_ind_korotus              0.0M
                             :muutostyot_laskutettu                           0.0M
                             :muutostyot_laskutettu_ind_korotettuna           0.0M
                             :muutostyot_laskutettu_ind_korotus               0.0M
                             :nimi                                            "Soratien hoito"
                             :sakot_laskutetaan                               1800.0M
                             :sakot_laskutetaan_ind_korotettuna               1830.4500478774338000M
                             :sakot_laskutetaan_ind_korotus                   30.4500478774338000M
                             :sakot_laskutettu                                0.0M
                             :sakot_laskutettu_ind_korotettuna                0.0M
                             :sakot_laskutettu_ind_korotus                    0.0M
                             :suolasakot_laskutetaan                          0.0M
                             :suolasakot_laskutetaan_ind_korotettuna          0.0M
                             :suolasakot_laskutetaan_ind_korotus              0.0M
                             :suolasakot_laskutettu                           0.0M
                             :suolasakot_laskutettu_ind_korotettuna           0.0M
                             :suolasakot_laskutettu_ind_korotus               0.0M
                             :tpi                                             6
                             :tuotekoodi                                      "23120"
                             :yht_laskutetaan                                 0.0M
                             :yht_laskutetaan_ind_korotettuna                 0.0M
                             :yht_laskutetaan_ind_korotus                     0.0M
                             :yht_laskutettu                                  0.0M
                             :yht_laskutettu_ind_korotettuna                  0.0M
                             :yht_laskutettu_ind_korotus                      0.0M}

          ]

      (is (= odotetut-talvihoito haetut-tiedot-talvihoito) "laskutusyhteenvedon-tiedot talvihoito")
      (is (= odotetut-liikenneymparisto haetut-tiedot-liikenneymparisto) "laskutusyhteenvedon-tiedot liikenneympäristön hoito")
      (is (= odotetut-soratiet haetut-tiedot-soratiet) "laskutusyhteenvedon-tiedot sorateiden hoito"))))
