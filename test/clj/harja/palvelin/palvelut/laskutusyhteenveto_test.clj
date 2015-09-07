(ns harja.palvelin.palvelut.laskutusyhteenveto_test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.raportit :refer :all]
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
                        :hae-laskutusyhteenvedon-tiedot (component/using
                                                          (->Raportit)
                                                          [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      tietokanta-fixture
                      (compose-fixtures jarjestelma-fixture urakkatieto-fixture)))

(deftest laskutusyhteenvedon-tietojen-haku
  (testing "laskutusyhteenvedon-tietojen-haku"
    (let [payload {:urakka-id          @oulun-alueurakan-2014-2019-id
                   :hk-alkupvm         (java.sql.Date. 114 9 1)
                   :hk-loppupvm        (java.sql.Date. 115 8 30)
                   :aikavali-alkupvm   (java.sql.Date. 115 7 1)
                    :aikavali-loppupvm (java.sql.Date. 115 7 30)}
          haetut-tiedot (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-laskutusyhteenvedon-tiedot
                                        +kayttaja-jvh+
                                        payload)
          haetut-tiedot-talvihoito (first (filter #(= (:tuotekoodi %) "23100") haetut-tiedot))
          haetut-tiedot-liikenneymparisto (first (filter #(= (:tuotekoodi %) "23110") haetut-tiedot))
          haetut-tiedot-soratiet (first (filter #(= (:tuotekoodi %) "23120") haetut-tiedot))
          _ (log/debug "haetut-tiedot-talvihoito" haetut-tiedot-talvihoito)
          _ (log/debug "haetut-tiedot-liikenneymparisto" haetut-tiedot-liikenneymparisto)
          _ (log/debug "haetut-tiedot-soratiet" haetut-tiedot-soratiet)

          odotetut-talvihoito {:tuotekoodi                             "23100" :nimi "Talvihoito"
                               :yht_laskutettu_ind_korotettuna         209.8 :suolasakot_laskutetaan 2280.0 :sakot_laskutetaan_ind_korotettuna 0.0
                               :muutostyot_laskutettu                  1000.0 :kht_laskutettu 35000.0 :kht_laskutetaan_ind_korotettuna 3717.0
                               :yht_laskutettu_ind_korotus             9.8 :muutostyot_laskutettu_ind_korotus 59.0 :kht_laskutetaan 3500.0
                               :sakot_laskutettu_ind_korotettuna       103.9 :kht_laskutettu_ind_korotus 1673.0 :sakot_laskutetaan 0.0
                               :kht_laskutetaan_ind_korotus            217.0 :yht_laskutetaan 0.0 :muutostyot_laskutetaan_ind_korotus 0.0
                               :suolasakot_laskutettu_ind_korotettuna  0.0 :muutostyot_laskutetaan 0.0 :suolasakot_laskutetaan_ind_korotus 108.68000000000008
                               :yht_laskutetaan_ind_korotettuna        0.0 :yht_laskutettu 200.0 :muutostyot_laskutettu_ind_korotettuna 1059.0
                               :muutostyot_laskutetaan_ind_korotettuna 0.0 :sakot_laskutetaan_ind_korotus 0.0 :suolasakot_laskutettu 0.0
                               :suolasakot_laskutetaan_ind_korotettuna 2388.6800000000003 :sakot_laskutettu_ind_korotus 3.9
                               :sakot_laskutettu                       100.0 :kht_laskutettu_ind_korotettuna 36673.0 :suolasakot_laskutettu_ind_korotus 0.0 :yht_laskutetaan_ind_korotus 0.0}

          odotetut-liikenneymparisto {:tuotekoodi                            "23110" :nimi "Liikenneympäristön hoito"
                                      :yht_laskutettu_ind_korotettuna        1039.0 :suolasakot_laskutetaan 0.0 :sakot_laskutetaan_ind_korotettuna 0.0
                                      :muutostyot_laskutettu                 2000.0 :kht_laskutettu 0.0 :kht_laskutetaan_ind_korotettuna 0.0
                                      :yht_laskutettu_ind_korotus            39.0 :muutostyot_laskutettu_ind_korotus 118.0 :kht_laskutetaan 0.0
                                      :sakot_laskutettu_ind_korotettuna      1226.0 :kht_laskutettu_ind_korotus 0.0 :sakot_laskutetaan 0.0
                                      :kht_laskutetaan_ind_korotus           0.0 :yht_laskutetaan 4000.0 :muutostyot_laskutetaan_ind_korotus 372.0
                                      :suolasakot_laskutettu_ind_korotettuna 0.0 :muutostyot_laskutetaan 6000.0 :suolasakot_laskutetaan_ind_korotus 0.0
                                      :yht_laskutetaan_ind_korotettuna       3186.0 :yht_laskutettu 1000.0
                                      :muutostyot_laskutettu_ind_korotettuna 2118.0 :muutostyot_laskutetaan_ind_korotettuna 6372.0
                                      :sakot_laskutetaan_ind_korotus         0.0 :suolasakot_laskutettu 0.0 :suolasakot_laskutetaan_ind_korotettuna 0.0
                                      :sakot_laskutettu_ind_korotus 26.0 :sakot_laskutettu 1200.0 :kht_laskutettu_ind_korotettuna 0.0
                                      :suolasakot_laskutettu_ind_korotus 0.0 :yht_laskutetaan_ind_korotus 186.0}

          odotetut-soratiet {:tuotekoodi                             "23120" :nimi "Soratien hoito"
                             :yht_laskutettu_ind_korotettuna         0.0 :suolasakot_laskutetaan 0.0 :sakot_laskutetaan_ind_korotettuna 849.6
                             :muutostyot_laskutettu                  0.0 :kht_laskutettu 100000.0 :kht_laskutetaan_ind_korotettuna 10620.0 :yht_laskutettu_ind_korotus 0.0
                             :muutostyot_laskutettu_ind_korotus      0.0 :kht_laskutetaan 10000.0 :sakot_laskutettu_ind_korotettuna 0.0 :kht_laskutettu_ind_korotus 4780.0
                             :sakot_laskutetaan                      800.0 :kht_laskutetaan_ind_korotus 620.0 :yht_laskutetaan 0.0 :muutostyot_laskutetaan_ind_korotus 0.0
                             :suolasakot_laskutettu_ind_korotettuna  0.0 :muutostyot_laskutetaan 0.0 :suolasakot_laskutetaan_ind_korotus 0.0
                             :yht_laskutetaan_ind_korotettuna        0.0 :yht_laskutettu 0.0 :muutostyot_laskutettu_ind_korotettuna 0.0
                             :muutostyot_laskutetaan_ind_korotettuna 0.0 :sakot_laskutetaan_ind_korotus 49.6 :suolasakot_laskutettu 0.0
                             :suolasakot_laskutetaan_ind_korotettuna 0.0 :sakot_laskutettu_ind_korotus 0.0 :sakot_laskutettu 0.0
                             :kht_laskutettu_ind_korotettuna 104780.0 :suolasakot_laskutettu_ind_korotus 0.0 :yht_laskutetaan_ind_korotus 0.0}

          ]

      (is (= odotetut-talvihoito haetut-tiedot-talvihoito) "laskutusyhteenvedon-tiedot talvihoito")
      (is (= odotetut-liikenneymparisto haetut-tiedot-liikenneymparisto) "laskutusyhteenvedon-tiedot liikenneympäristön hoito")
      (is (= odotetut-soratiet haetut-tiedot-soratiet) "laskutusyhteenvedon-tiedot sorateiden hoito"))))





