(ns harja.palvelin.palvelut.laskutusyhteenveto-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto :as laskutusyhteenveto]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      tietokanta-fixture
                      (compose-fixtures jarjestelma-fixture urakkatieto-fixture)))

(deftest laskutusyhteenvedon-tietojen-haku
  (testing "laskutusyhteenvedon-tietojen-haku"
    (let [haetut-tiedot-oulu (laskutusyhteenveto/hae-laskutusyhteenvedon-tiedot
                               (:db jarjestelma)
                               +kayttaja-jvh+
                               {:urakka-id @oulun-alueurakan-2014-2019-id
                                :alkupvm   (pvm/->pvm "1.8.2015")
                                :loppupvm (pvm/->pvm "31.8.2015")})
          haetut-tiedot-kajaani (laskutusyhteenveto/hae-laskutusyhteenvedon-tiedot
                                  (:db jarjestelma)
                                  +kayttaja-jvh+
                                  {:urakka-id @kajaanin-alueurakan-2014-2019-id
                                   :alkupvm   (pvm/->pvm "1.8.2015")
                                   :loppupvm  (pvm/->pvm "31.8.2015")})
          poista-tpi (fn [tiedot]
                       (map #(dissoc % :tpi) tiedot))
          haetut-tiedot-oulu-ilman-tpita (poista-tpi haetut-tiedot-oulu)
          haetut-tiedot-kajaani-ilman-tpita (poista-tpi haetut-tiedot-kajaani)

          haetut-tiedot-oulu-talvihoito (first (filter #(= (:tuotekoodi %) "23100") haetut-tiedot-oulu))
          haetut-tiedot-oulu-liikenneymparisto (first (filter #(= (:tuotekoodi %) "23110") haetut-tiedot-oulu))
          haetut-tiedot-oulu-soratiet (first (filter #(= (:tuotekoodi %) "23120") haetut-tiedot-oulu))
          _ (log/debug "haetut-tiedot-oulu-talvihoito" haetut-tiedot-oulu-talvihoito)
          _ (log/debug "haetut-tiedot-oulu-liikenneymparisto" haetut-tiedot-oulu-liikenneymparisto)
          _ (log/debug "haetut-tiedot-oulu-soratiet" haetut-tiedot-oulu-soratiet)

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
                               :kaikki_laskutetaan                              4309.8340248962656517440000000M
                               :kaikki_laskutetaan_ind_korotus                  89.8340248962656517440000000M
                               :kaikki_laskutettu                               39229.07756144270691680200M
                               :kaikki_laskutettu_ind_korotus                   129.07756144270691680200M
                               :kaikki_paitsi_kht_laskutetaan                   809.8340248962656517440000000M
                               :kaikki_paitsi_kht_laskutetaan_ind_korotus       30.6255984679221517440000000M
                               :kaikki_paitsi_kht_laskutettu                    4229.07756144270691680200M
                               :kaikki_paitsi_kht_laskutettu_ind_korotus        12.89498882859880851200M
                               :kht_laskutetaan                                 3500.0M
                               :kht_laskutetaan_ind_korotettuna                 3559.2084264283435000M
                               :kht_laskutetaan_ind_korotus                     59.2084264283435000M
                               :kht_laskutettu                                  35000.0M
                               :kht_laskutettu_ind_korotettuna                  35116.18257261410810829000M
                               :kht_laskutettu_ind_korotus                      116.18257261410810829000M
                               :lampotila_puuttuu                               false
                               :muutostyot_laskutetaan                          1000.0M
                               :muutostyot_laskutetaan_ind_korotettuna          1016.91669326524100000M
                               :muutostyot_laskutetaan_ind_korotus              16.91669326524100000M
                               :muutostyot_laskutettu                           1000.0M
                               :muutostyot_laskutettu_ind_korotettuna           1014.04404723906800000M
                               :muutostyot_laskutettu_ind_korotus               14.04404723906800000M
                               :nimi                                            "Talvihoito"
                               :perusluku                                       104.4333333333333333M
                               :sakot_laskutetaan                               0.0M
                               :sakot_laskutetaan_ind_korotettuna               0.0M
                               :sakot_laskutetaan_ind_korotus                   0.0M
                               :sakot_laskutettu                                100.0M
                               :sakot_laskutettu_ind_korotettuna                99.48930737312480054200M
                               :sakot_laskutettu_ind_korotus                    -0.51069262687519945800M
                               :suolasakko_kaytossa                             true
                               :suolasakot_laskutetaan                          -2280.00000M
                               :suolasakot_laskutetaan_ind_korotettuna          -2287.2773699329715941860000000M
                               :suolasakot_laskutetaan_ind_korotus              -7.2773699329715941860000000M
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
                                      :lampotila_puuttuu                               false
                                      :muutostyot_laskutetaan                          8000.0M
                                      :muutostyot_laskutetaan_ind_korotettuna          8033.83338653048200000M
                                      :muutostyot_laskutetaan_ind_korotus              33.83338653048200000M
                                      :muutostyot_laskutettu                           3000.0M
                                      :muutostyot_laskutettu_ind_korotettuna           3014.04404723906800000M
                                      :muutostyot_laskutettu_ind_korotus               14.04404723906800000M
                                      :nimi                                            "Liikenneympäristön hoito"
                                      :perusluku                                       104.4333333333333333M
                                      :sakot_laskutetaan                               0.0M
                                      :sakot_laskutetaan_ind_korotettuna               0.0M
                                      :sakot_laskutetaan_ind_korotus                   0.0M
                                      :sakot_laskutettu                                1200.0M
                                      :sakot_laskutettu_ind_korotettuna                1203.6706032556655000M
                                      :sakot_laskutettu_ind_korotus                    3.6706032556655000M
                                      :suolasakko_kaytossa                             true
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
                             :lampotila_puuttuu                               false
                             :muutostyot_laskutetaan                          0.0M
                             :muutostyot_laskutetaan_ind_korotettuna          0.0M
                             :muutostyot_laskutetaan_ind_korotus              0.0M
                             :muutostyot_laskutettu                           0.0M
                             :muutostyot_laskutettu_ind_korotettuna           0.0M
                             :muutostyot_laskutettu_ind_korotus               0.0M
                             :nimi                                            "Soratien hoito"
                             :perusluku                                       104.4333333333333333M
                             :sakot_laskutetaan                               1800.0M
                             :sakot_laskutetaan_ind_korotettuna               1830.4500478774338000M
                             :sakot_laskutetaan_ind_korotus                   30.4500478774338000M
                             :sakot_laskutettu                                0.0M
                             :sakot_laskutettu_ind_korotettuna                0.0M
                             :sakot_laskutettu_ind_korotus                    0.0M
                             :suolasakko_kaytossa                             true
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


      (is (= haetut-tiedot-oulu-ilman-tpita haetut-tiedot-kajaani-ilman-tpita))
      (is (= odotetut-talvihoito haetut-tiedot-oulu-talvihoito) "laskutusyhteenvedon-tiedot talvihoito")
      (is (= odotetut-liikenneymparisto haetut-tiedot-oulu-liikenneymparisto) "laskutusyhteenvedon-tiedot liikenneympäristön hoito")
      (is (= odotetut-soratiet haetut-tiedot-oulu-soratiet) "laskutusyhteenvedon-tiedot sorateiden hoito"))))


;; HAR-1959: Laskutusyhteenveto ottaa talvisuolasakon väärään hoitokauteen loka-joulukuussa
(deftest suolasakko-oikean-vuoden-laskutusyhteenvedossa
  (testing "suolasakko-oikean-vuoden-laskutusyhteenvedossa"
    (let [haetut-tiedot-oulu (laskutusyhteenveto/hae-laskutusyhteenvedon-tiedot
                               (:db jarjestelma)
                               +kayttaja-jvh+
                               {:urakka-id @oulun-alueurakan-2014-2019-id
                                :alkupvm   (pvm/->pvm "1.10.2014")
                                :loppupvm (pvm/->pvm "31.10.2014")})
          haetut-tiedot-oulu-talvihoito (first (filter #(= (:tuotekoodi %) "23100") haetut-tiedot-oulu))]

      (is (= (:suolasakko_kaytossa haetut-tiedot-oulu-talvihoito) true) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutettu haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutettu_ind_korotettuna haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutettu_ind_korotus haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutetaan haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutetaan_ind_korotettuna haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutetaan_ind_korotus haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa"))))