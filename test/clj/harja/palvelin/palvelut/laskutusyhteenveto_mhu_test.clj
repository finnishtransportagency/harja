(ns harja.palvelin.palvelut.laskutusyhteenveto-mhu-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-mhu :as laskutusyhteenveto]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as lyv-yhteiset]

            [harja.palvelin.palvelut.yksikkohintaiset-tyot :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [harja.testi :as testi]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      tietokanta-fixture
                      (compose-fixtures jarjestelma-fixture urakkatieto-fixture)))

(deftest laskutusyhteenvedon-tietojen-haku
  (testing "laskutusyhteenvedon-tietojen-haku"
    (let [haetut-tiedot-oulu (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                               (:db jarjestelma)
                               +kayttaja-jvh+
                               {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                :urakkatyyppi "teiden-hoito"
                                :alkupvm (pvm/->pvm "1.3.2020") ;; (pvm/->pvm "1.3.2020") (pvm/hoitokauden-alkupvm (pvm/vuosi (pvm/nyt)))
                                :loppupvm (pvm/->pvm "31.3.2020")}) ;; (pvm/->pvm "31.3.2020") (pvm/hoitokauden-loppupvm (pvm/vuosi (pvm/nyt)))

          poista-tpi (fn [tiedot]
                       (map #(dissoc %
                                     :tpi) tiedot))
          haetut-tiedot-oulu-ilman-tpita (poista-tpi haetut-tiedot-oulu)
          ;haetut-tiedot-kajaani-ilman-tpita (poista-tpi haetut-tiedot-kajaani)

          haetut-tiedot-oulu-talvihoito (first (filter #(= (:tuotekoodi %) "23100") haetut-tiedot-oulu))
          haetut-tiedot-oulu-liikenneymparisto (first (filter #(= (:tuotekoodi %) "23110") haetut-tiedot-oulu))
          haetut-tiedot-oulu-soratiet (first (filter #(= (:tuotekoodi %) "23120") haetut-tiedot-oulu))
          haetut-tiedot-oulu-mhu-ja-hoidon-johto (first (filter #(= (:tuotekoodi %) "23150") haetut-tiedot-oulu))
          haetut-tiedot-oulu-paallyste (first (filter #(= (:tuotekoodi %) "20100") haetut-tiedot-oulu))
          haetut-tiedot-oulu-mhu-yllapito (first (filter #(= (:tuotekoodi %) "20190") haetut-tiedot-oulu))
          haetut-tiedot-oulu-mhu-korvausinvestointi (first (filter #(= (:tuotekoodi %) "14300") haetut-tiedot-oulu))
          ;; TODO: assertit testidataan pohjautuen eri toimenpideinstansseille. Luodaan lisää dataa jos sitä on liian vähän
          _ (log/debug "haetut-tiedot-oulu-talvihoito")
          _ (clojure.pprint/pprint haetut-tiedot-oulu-talvihoito)
          _ (log/debug "haetut-tiedot-oulu-liikenneymparisto" )
          _ (clojure.pprint/pprint haetut-tiedot-oulu-liikenneymparisto)
          _ (log/debug "haetut-tiedot-oulu-soratiet" )
          _ (clojure.pprint/pprint haetut-tiedot-oulu-soratiet)
          _ (log/debug "haetut-tiedot-oulu-mhu-korvausinvestointi" )
          _ (clojure.pprint/pprint haetut-tiedot-oulu-mhu-korvausinvestointi)
          odotetut-talvihoito {:bonukset_laskutetaan 0.0M,
                               :suolasakot_laskutetaan 0.0M,
                               :kht_laskutettu 3000.77M,
                               :kaikki_laskutetaan 2000.20M,
                               :kaikki_laskutettu 3000.77M,
                               :kht_laskutetaan 2000.20M,
                               :mt_laskutettu 0.0M,
                               :bonukset_laskutettu 0.0M,
                               :sakot_laskutetaan 0.0M,
                               :kit_laskutetaan 200.20M,
                               :nimi "Talvihoito",
                               :mt_laskutetaan 0.0M,
                               :kit_laskutettu 300.77M,
                               :lampotila_puuttuu true,
                               :aht_laskutetaan 0.0M,
                               :perusluku nil,
                               :kat_laskutettu 300.77M,
                               :suolasakot_laskutettu 5000.77M,
                               :aht_laskutettu 0.0M,
                               :kat_laskutetaan 200.20M,
                               :tuotekoodi "23100",
                               :sakot_laskutettu 5000.20M,
                               :suolasakko_kaytossa false,
                               :tpi 45}
          odotetut-liikenneymparistot {:bonukset_laskutetaan 0.0M,
                                       :suolasakot_laskutetaan 0.0M,
                                       :kht_laskutettu 666.66M,
                                       :kaikki_laskutetaan 0.0M,
                                       :kaikki_laskutettu 5111.10M,
                                       :kht_laskutetaan 0.0M,
                                       :mt_laskutettu 0.0M,
                                       :bonukset_laskutettu 0.0M,
                                       :sakot_laskutetaan 0.0M,
                                       :kit_laskutetaan 0.0M,
                                       :nimi "Liikenneympäristön hoito",
                                       :mt_laskutetaan 0.0M,
                                       :kit_laskutettu 0.0M,
                                       :lampotila_puuttuu true,
                                       :aht_laskutetaan 0.0M,
                                       :perusluku nil,
                                       :kat_laskutettu 0.0M,
                                       :suolasakot_laskutettu 0.0M,
                                       :aht_laskutettu 4444.44M,
                                       :kat_laskutetaan 0.0M,
                                       :tuotekoodi "23110",
                                       :sakot_laskutettu 0.0M,
                                       :suolasakko_kaytossa false,
                                       :tpi 46}
          odotetut-soratiet {:bonukset_laskutetaan 0.0M,
                             :suolasakot_laskutetaan 0.0M,
                             :kht_laskutettu 4000.77M,
                             :kaikki_laskutetaan 0.0M,
                             :kaikki_laskutettu 4000.77M,
                             :kht_laskutetaan 0.0M,
                             :mt_laskutettu 0.0M,
                             :bonukset_laskutettu 0.0M,
                             :sakot_laskutetaan 0.0M,
                             :kit_laskutetaan 0.0M,
                             :nimi "Soratien hoito",
                             :mt_laskutetaan 0.0M,
                             :kit_laskutettu 400.77M,
                             :lampotila_puuttuu true,
                             :aht_laskutetaan 0.0M,
                             :perusluku nil,
                             :kat_laskutettu 400.77M,
                             :suolasakot_laskutettu 0.0M,
                             :aht_laskutettu 0.0M,
                             :kat_laskutetaan 0.0M,
                             :tuotekoodi "23120",
                             :sakot_laskutettu 0.0M,
                             :suolasakko_kaytossa false,
                             :tpi 47}
          odotetut-korvausinvestoinnit {:bonukset_laskutetaan 0.0M,
                                        :suolasakot_laskutetaan 0.0M,
                                        :kht_laskutettu 6000.77M,
                                        :kaikki_laskutetaan 6000.20M,
                                        :kaikki_laskutettu 6000.77M,
                                        :kht_laskutetaan 6000.20M,
                                        :mt_laskutettu 0.0M,
                                        :bonukset_laskutettu 0.0M,
                                        :sakot_laskutetaan 0.0M,
                                        :kit_laskutetaan 600.20M,
                                        :nimi "MHU Korvausinvestointi",
                                        :mt_laskutetaan 0.0M,
                                        :kit_laskutettu 600.77M,
                                        :lampotila_puuttuu true,
                                        :aht_laskutetaan 0.0M,
                                        :perusluku nil,
                                        :kat_laskutettu 600.77M,
                                        :suolasakot_laskutettu 0.0M,
                                        :aht_laskutettu 0.0M,
                                        :kat_laskutetaan 600.20M,
                                        :tuotekoodi "14300",
                                        :sakot_laskutettu 0.0M,
                                        :suolasakko_kaytossa false,
                                        :tpi 51}
          ]

      (testing "Talvihoito"
        (testi/tarkista-map-arvot odotetut-talvihoito haetut-tiedot-oulu-talvihoito))
      (testing "Liikenneympäristön hoito"
        (testi/tarkista-map-arvot odotetut-liikenneymparistot haetut-tiedot-oulu-liikenneymparisto))
      (testing "Liikenneympäristön hoito"
        (testi/tarkista-map-arvot odotetut-soratiet haetut-tiedot-oulu-soratiet))
      (testing "MHU Korvausinvestointi"
        (testi/tarkista-map-arvot odotetut-korvausinvestoinnit haetut-tiedot-oulu-mhu-korvausinvestointi))
      )))


(deftest tiedot-haetaan-oikein-maksuera-laskentaa-varten
  (testing "tiedot-haetaan-oikein-maksuera-laskentaa-varten"
    (let [haetut-tiedot-oulu (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                               (:db jarjestelma)
                               +kayttaja-jvh+
                               {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                :alkupvm (pvm/hoitokauden-alkupvm (pvm/vuosi (pvm/nyt)))
                                :loppupvm (pvm/hoitokauden-loppupvm (pvm/vuosi (pvm/nyt)))})

          haetut-tiedot-oulu-liikenneympariston-hoito (first (filter #(= (:tuotekoodi %) "23110") haetut-tiedot-oulu))]
      (println " haetut tiedot liikenne" (select-keys haetut-tiedot-oulu-liikenneympariston-hoito
                                                      [:yht_laskutetaan :yht_laskutetaan_ind_korotus :yht_laskutetaan_ind_korotettuna]))

      (is (= (:yht_laskutetaan haetut-tiedot-oulu-liikenneympariston-hoito) 7882.5M) ":yht_laskutetaan laskutusyhteenvedossa")
      (is (= (:yht_laskutetaan_ind_korotus haetut-tiedot-oulu-liikenneympariston-hoito) 2310.387931034483003250M) ":yht_laskutetaan laskutusyhteenvedossa")
      (is (= (:yht_laskutetaan_ind_korotettuna haetut-tiedot-oulu-liikenneympariston-hoito) 10192.887931034483003250M) ":yht_laskutetaan laskutusyhteenvedossa"))))
