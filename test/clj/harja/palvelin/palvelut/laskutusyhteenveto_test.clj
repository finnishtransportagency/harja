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
                         {:urakka-id @oulun-alueurakan-2014-2019-id
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
                               :erilliskustannukset_laskutetaan                 2000.0M
                               :erilliskustannukset_laskutetaan_ind_korotettuna 2124.0000000000000000M
                               :erilliskustannukset_laskutetaan_ind_korotus     124.0000000000000000M
                               :erilliskustannukset_laskutettu                  1000.0M
                               :erilliskustannukset_laskutettu_ind_korotettuna  990.0000000000000000M
                               :erilliskustannukset_laskutettu_ind_korotus      -10.0000000000000000M
                               :kaikki_laskutetaan                              9291.680000000000076000000M
                               :kaikki_laskutetaan_ind_korotus                  511.680000000000076000000M
                               :kaikki_laskutettu                               40934.52000000000000000M
                               :kaikki_laskutettu_ind_korotus                   1824.52000000000000000M
                               :kaikki_paitsi_kht_laskutetaan                   5791.680000000000076000000M
                               :kaikki_paitsi_kht_laskutetaan_ind_korotus       294.680000000000076000000M
                               :kaikki_paitsi_kht_laskutettu                    5924.52000000000000000M
                               :kaikki_paitsi_kht_laskutettu_ind_korotus        150.90000000000000000M
                               :kht_laskutetaan                                 3500.0M
                               :kht_laskutetaan_ind_korotettuna                 3717.0000000000000000M
                               :kht_laskutetaan_ind_korotus                     217.0000000000000000M
                               :kht_laskutettu                                  35010.0M
                               :kht_laskutettu_ind_korotettuna                  36683.6200000000000000M
                               :kht_laskutettu_ind_korotus                      1673.6200000000000000M
                               :muutostyot_laskutetaan                          1000.0M
                               :muutostyot_laskutetaan_ind_korotettuna          1062.00000000000000000M
                               :muutostyot_laskutetaan_ind_korotus              62.00000000000000000M
                               :muutostyot_laskutettu                           1000.0M
                               :muutostyot_laskutettu_ind_korotettuna           1059.00000000000000000M
                               :muutostyot_laskutettu_ind_korotus               59.00000000000000000M
                               :nimi                                            "Talvihoito"
                               :sakot_laskutetaan                               0.0M
                               :sakot_laskutetaan_ind_korotettuna               0.0M
                               :sakot_laskutetaan_ind_korotus                   0.0M
                               :sakot_laskutettu                                100.0M
                               :sakot_laskutettu_ind_korotettuna                103.9000000000000000M
                               :sakot_laskutettu_ind_korotus                    3.9000000000000000M
                               :suolasakot_laskutetaan                          2280.00000M
                               :suolasakot_laskutetaan_ind_korotettuna          2388.680000000000076000000M
                               :suolasakot_laskutetaan_ind_korotus              108.680000000000076000000M
                               :suolasakot_laskutettu                           0.0M
                               :suolasakot_laskutettu_ind_korotettuna           0.0M
                               :suolasakot_laskutettu_ind_korotus               0.0M
                               :tuotekoodi                                      "23100"
                               :yht_laskutetaan                                 0.0M
                               :yht_laskutetaan_ind_korotettuna                 0.0M
                               :yht_laskutetaan_ind_korotus                     0.0M
                               :yht_laskutettu                                  2000.0M
                               :yht_laskutettu_ind_korotettuna                  2098.0000000000000000M
                               :yht_laskutettu_ind_korotus                      98.0000000000000000M}

          odotetut-liikenneymparisto {:akilliset_hoitotyot_laskutetaan                 2000.0M
                                      :akilliset_hoitotyot_laskutetaan_ind_korotettuna 2062.00000000000000000M
                                      :akilliset_hoitotyot_laskutetaan_ind_korotus     62.00000000000000000M
                                      :akilliset_hoitotyot_laskutettu                  1000.0M
                                      :akilliset_hoitotyot_laskutettu_ind_korotettuna  1059.00000000000000000M
                                      :akilliset_hoitotyot_laskutettu_ind_korotus      59.00000000000000000M
                                      :yht_laskutettu_ind_korotettuna 1039.0000000000000000M,
                                      :suolasakot_laskutetaan 0.0M,
                                      :kaikki_laskutetaan_ind_korotus 248.00000000000000000M,
                                      :sakot_laskutetaan_ind_korotettuna 0.0M,
                                      :kaikki_paitsi_kht_laskutettu_ind_korotus 124.00000000000000000M,
                                      :muutostyot_laskutettu 3000.0M,
                                      :kht_laskutettu 0.0M,
                                      :kaikki_laskutetaan 10248.00000000000000000M,
                                      :kht_laskutetaan_ind_korotettuna 0.0M,
                                      :yht_laskutettu_ind_korotus 39.0000000000000000M,
                                      :kaikki_laskutettu 5324.00000000000000000M,
                                      :muutostyot_laskutettu_ind_korotus 59.00000000000000000M,
                                      :kht_laskutetaan 0.0M,
                                      :kaikki_paitsi_kht_laskutettu 5324.00000000000000000M,
                                      :sakot_laskutettu_ind_korotettuna 1226.0000000000000000M,
                                      :kht_laskutettu_ind_korotus 0.0M,
                                      :sakot_laskutetaan 0.0M,
                                      :kht_laskutetaan_ind_korotus 0.0M,
                                      :yht_laskutetaan 3000.0M,
                                      :erilliskustannukset_laskutettu_ind_korotus 0.0M,
                                      :muutostyot_laskutetaan_ind_korotus 62.00000000000000000M,
                                      :suolasakot_laskutettu_ind_korotettuna 0.0M,
                                      :muutostyot_laskutetaan 7000.0M,
                                      :suolasakot_laskutetaan_ind_korotus 0.0M,
                                      :erilliskustannukset_laskutetaan 0.0M,
                                      :yht_laskutetaan_ind_korotettuna 3186.0000000000000000M,
                                      :nimi "Liikenneympäristön hoito",
                                      :yht_laskutettu 1000.0M,
                                      :muutostyot_laskutettu_ind_korotettuna 3059.00000000000000000M,
                                      :erilliskustannukset_laskutettu_ind_korotettuna 0.0M,
                                      :muutostyot_laskutetaan_ind_korotettuna 7062.00000000000000000M,
                                      :erilliskustannukset_laskutetaan_ind_korotettuna 0.0M,
                                      :sakot_laskutetaan_ind_korotus 0.0M,
                                      :kaikki_paitsi_kht_laskutetaan 10248.00000000000000000M,
                                      :suolasakot_laskutettu 0.0M,
                                      :kaikki_paitsi_kht_laskutetaan_ind_korotus 248.00000000000000000M,
                                      :suolasakot_laskutetaan_ind_korotettuna 0.0M,
                                      :kaikki_laskutettu_ind_korotus 124.00000000000000000M,
                                      :erilliskustannukset_laskutetaan_ind_korotus 0.0M,
                                      :tuotekoodi "23110",
                                      :sakot_laskutettu_ind_korotus 26.0000000000000000M,
                                      :sakot_laskutettu 1200.0M,
                                      :erilliskustannukset_laskutettu 0.0M,
                                      :kht_laskutettu_ind_korotettuna 0.0M,
                                      :suolasakot_laskutettu_ind_korotus 0.0M,
                                      :yht_laskutetaan_ind_korotus 186.0000000000000000M}          

          odotetut-soratiet {:akilliset_hoitotyot_laskutetaan                 0.0M
                             :akilliset_hoitotyot_laskutetaan_ind_korotettuna 0.0M
                             :akilliset_hoitotyot_laskutetaan_ind_korotus     0.0M
                             :akilliset_hoitotyot_laskutettu                  0.0M
                             :akilliset_hoitotyot_laskutettu_ind_korotettuna  0.0M
                             :akilliset_hoitotyot_laskutettu_ind_korotus      0.0M
                             :yht_laskutettu_ind_korotettuna 0.0M,
                             :suolasakot_laskutetaan 0.0M,
                             :kaikki_laskutetaan_ind_korotus 731.6000000000000000M,
                             :sakot_laskutetaan_ind_korotettuna 1911.6000000000000000M,
                             :kaikki_paitsi_kht_laskutettu_ind_korotus 0.0M,
                             :muutostyot_laskutettu 0.0M,
                             :kht_laskutettu 100000.0M,
                             :kaikki_laskutetaan 12531.6000000000000000M,
                             :kht_laskutetaan_ind_korotettuna 10620.0000000000000000M,
                             :yht_laskutettu_ind_korotus 0.0M,
                             :kaikki_laskutettu 104780.0000000000000000M,
                             :muutostyot_laskutettu_ind_korotus 0.0M,
                             :kht_laskutetaan 10000M,
                             :kaikki_paitsi_kht_laskutettu 4780.0000000000000000M,
                             :sakot_laskutettu_ind_korotettuna 0.0M,
                             :kht_laskutettu_ind_korotus 4780.0000000000000000M,
                             :sakot_laskutetaan 1800.0M,
                             :kht_laskutetaan_ind_korotus 620.0000000000000000M,
                             :yht_laskutetaan 0.0M,
                             :erilliskustannukset_laskutettu_ind_korotus 0.0M,
                             :muutostyot_laskutetaan_ind_korotus 0.0M,
                             :suolasakot_laskutettu_ind_korotettuna 0.0M,
                             :muutostyot_laskutetaan 0.0M,
                             :suolasakot_laskutetaan_ind_korotus 0.0M,
                             :erilliskustannukset_laskutetaan 0.0M,
                             :yht_laskutetaan_ind_korotettuna 0.0M,
                             :nimi "Soratien hoito",
                             :yht_laskutettu 0.0M,
                             :muutostyot_laskutettu_ind_korotettuna 0.0M,
                             :erilliskustannukset_laskutettu_ind_korotettuna 0.0M,
                             :muutostyot_laskutetaan_ind_korotettuna 0.0M,
                             :erilliskustannukset_laskutetaan_ind_korotettuna 0.0M,
                             :sakot_laskutetaan_ind_korotus 111.6000000000000000M,
                             :kaikki_paitsi_kht_laskutetaan 2531.6000000000000000M,
                             :suolasakot_laskutettu 0.0M,
                             :kaikki_paitsi_kht_laskutetaan_ind_korotus 111.6000000000000000M,
                             :suolasakot_laskutetaan_ind_korotettuna 0.0M,
                             :kaikki_laskutettu_ind_korotus 4780.0000000000000000M,
                             :erilliskustannukset_laskutetaan_ind_korotus 0.0M,
                             :tuotekoodi "23120",
                             :sakot_laskutettu_ind_korotus 0.0M,
                             :sakot_laskutettu 0.0M,
                             :erilliskustannukset_laskutettu 0.0M,
                             :kht_laskutettu_ind_korotettuna 104780.0000000000000000M,
                             :suolasakot_laskutettu_ind_korotus 0.0M,
                             :yht_laskutetaan_ind_korotus 0.0M}
          
          ]

      (is (= odotetut-talvihoito haetut-tiedot-talvihoito) "laskutusyhteenvedon-tiedot talvihoito")
      (is (= odotetut-liikenneymparisto haetut-tiedot-liikenneymparisto) "laskutusyhteenvedon-tiedot liikenneympäristön hoito")
      (is (= odotetut-soratiet haetut-tiedot-soratiet) "laskutusyhteenvedon-tiedot sorateiden hoito"))))
