(ns harja.palvelin.palvelut.laskutusyhteenveto-cache-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as lyv-yhteiset]
            [harja.palvelin.palvelut.yksikkohintaiset-tyot :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [harja.testi :as testi]
            [harja.palvelin.palvelut.toteumat :as toteumat]
            [harja.palvelin.palvelut.muut-tyot :as muut-tyot]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (let [tietokanta (tietokanta/luo-tietokanta testitietokanta)]
                      (component/start
                       (component/system-map
                        :db tietokanta
                        :db-replica tietokanta
                        :http-palvelin (testi-http-palvelin)
                        :yksikkohintaiset-tyot (component/using
                                                (->Yksikkohintaiset-tyot)
                                                [:http-palvelin :db])
                        :toteumat (component/using
                                   (toteumat/->Toteumat)
                                   [:http-palvelin :db :db-replica])
                        :muut-tyot (component/using
                                    (muut-tyot/->Muut-tyot)
                                    [:http-palvelin :db]))))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      tietokanta-fixture
                      (compose-fixtures jarjestelma-fixture urakkatieto-fixture)))

(def +tuotekoodi-talvihoito+ "23100")
(def +tuotekoodi-liikenneympariston-hoito+ "23110")
(def +tuotekoodi-soratien-hoito+ "23120")

(defn- tyhjenna-urakan-cache [urakka-id]
  (u "DELETE FROM laskutusyhteenveto_cache WHERE urakka = " urakka-id ";"))

(defn- hae-cachesta-kentat [urakka-id tuotekoodi]
  (first (q-map
           "SELECT y.urakka,
                    sum((y.rivi).muutostyot_laskutettu) as muutostyot_laskutettu,
                    sum((y.rivi).muutostyot_laskutetaan) as muutostyot_laskutetaan,
                    sum((y.rivi).erilliskustannukset_laskutettu) as erilliskustannukset_laskutettu,
                    sum((y.rivi).erilliskustannukset_laskutetaan) as erilliskustannukset_laskutetaan,
                    sum((y.rivi).yht_laskutettu) as yht_laskutettu,
                    sum((y.rivi).yht_laskutetaan) as yht_laskutetaan,
                    sum((y.rivi).kaikki_laskutettu) as kaikki_laskutettu,
                    sum((y.rivi).kaikki_laskutetaan) as kaikki_laskutetaan,
                    sum((y.rivi).kaikki_laskutettu_ind_korotus) as kaikki_laskutettu_ind_korotus,
                    sum((y.rivi).kaikki_laskutetaan_ind_korotus) as kaikki_laskutetaan_ind_korotus
             FROM (SELECT unnest(x.rivit) as rivi, x.nimi as urakka
                   FROM (SELECT c.urakka, c.alkupvm, c.loppupvm, c.tallennettu, c.rivit, u.nimi
                         FROM laskutusyhteenveto_cache c
                              JOIN urakka u ON u.id = c.urakka
                  WHERE c.urakka = " urakka-id " AND c.alkupvm = '2015-8-01') x) y WHERE (y.rivi).tuotekoodi = '" tuotekoodi "' GROUP BY y.urakka order by y.urakka;")))

(deftest laskutusyhteenvedon-cache
  (testing "laskutusyhteenvedon-cache"
    (let [urakka-id @oulun-alueurakan-2014-2019-id
          varmista-tyhjyys (tyhjenna-urakan-cache urakka-id)
          cache-tyhja (first (q-map "SELECT * FROM laskutusyhteenveto_cache WHERE urakka = " urakka-id ";"))
          eka-ajo (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                               (:db jarjestelma) +kayttaja-jvh+
                               {:urakka-id urakka-id :alkupvm   (pvm/->pvm "1.8.2015")
                                :loppupvm (pvm/->pvm "31.8.2015")})
          toka-ajo (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                               (:db jarjestelma) +kayttaja-jvh+
                               {:urakka-id urakka-id :alkupvm   (pvm/->pvm "1.8.2015")
                                :loppupvm (pvm/->pvm "31.8.2015")})
          haetut-tiedot-oulu (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                               (:db jarjestelma) +kayttaja-jvh+
                               {:urakka-id urakka-id :alkupvm   (pvm/->pvm "1.8.2015")
                                :loppupvm (pvm/->pvm "31.8.2015")})
          cachesta-haettu-kysely (hae-cachesta-kentat urakka-id +tuotekoodi-liikenneympariston-hoito+)
          haetut-tiedot-oulu-liikenneympariston-hoito (first (filter #(= (:tuotekoodi %) +tuotekoodi-liikenneympariston-hoito+) haetut-tiedot-oulu))
          cache-count (:count (first (q-map "SELECT count(*) FROM laskutusyhteenveto_cache c WHERE c.urakka = " urakka-id " AND c.alkupvm = '2015-8-01';")))]
      (is (= cache-count 1) "Monta kyselyä samoin parametrein, silti vain yksi cachessa")
      (is (nil? cache-tyhja) "cache tyhjä ennen kyselyn ajoa")
      (is (some? cachesta-haettu-kysely) "cache ei tyhjä ennen kyselyn ajoa")
      (is (= eka-ajo toka-ajo haetut-tiedot-oulu) "Saman laskutusyhteenvedon eri ajokerrat antaa saman tulokset")
      (is (= 1000.0M
             (:yht_laskutettu haetut-tiedot-oulu-liikenneympariston-hoito)
             (:yht_laskutettu cachesta-haettu-kysely)) ":yht_laskutettu laskutusyhteenvedossa")
      (is (= 3000.0M
             (:yht_laskutetaan haetut-tiedot-oulu-liikenneympariston-hoito)
             (:yht_laskutetaan cachesta-haettu-kysely)) ":yht_laskutetaan laskutusyhteenvedossa")
      (is (= 3820.11494252873560900000M
             (:kaikki_laskutettu haetut-tiedot-oulu-liikenneympariston-hoito)
             (:kaikki_laskutettu cachesta-haettu-kysely)) ":kaikki_laskutettu laskutusyhteenvedossa")
      (is (= 13103.44827586206880000M
             (:kaikki_laskutetaan haetut-tiedot-oulu-liikenneympariston-hoito)
             (:kaikki_laskutetaan cachesta-haettu-kysely)) ":kaikki_laskutetaan laskutusyhteenvedossa")
      (is (= 20.11494252873560900000M
             (:kaikki_laskutettu_ind_korotus haetut-tiedot-oulu-liikenneympariston-hoito)
             (:kaikki_laskutettu_ind_korotus cachesta-haettu-kysely)) ":kaikki_laskutettu_ind_korotus laskutusyhteenvedossa")
      (is (= 103.44827586206880000M
             (:kaikki_laskutetaan_ind_korotus haetut-tiedot-oulu-liikenneympariston-hoito)
             (:kaikki_laskutetaan_ind_korotus cachesta-haettu-kysely)) ":kaikki_laskutetaan_ind_korotus laskutusyhteenvedossa"))))

(deftest laskutusyhteenvedon-cache-tyhjenee-jos-yksikkohinta-muuttuu
  (testing "laskutusyhteenvedon-cache-tyhjenee-jos-yksikkohinta-muuttuu"
    (let [urakka-id @oulun-alueurakan-2014-2019-id
          varmista-tyhjyys (tyhjenna-urakan-cache urakka-id)
          cache-tyhja (first (q-map "SELECT * FROM laskutusyhteenveto_cache WHERE urakka = " urakka-id ";"))
          eka-tietojen-haku (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                              (:db jarjestelma) +kayttaja-jvh+
                              {:urakka-id urakka-id :alkupvm (pvm/->pvm "1.8.2015")
                               :loppupvm (pvm/->pvm "31.8.2015")})
          cachesta-haettu-kysely (hae-cachesta-kentat urakka-id +tuotekoodi-liikenneympariston-hoito+)
          haetut-tiedot-oulu-liikenneympariston-hoito (first (filter #(= (:tuotekoodi %) +tuotekoodi-liikenneympariston-hoito+) eka-tietojen-haku))
          cache-count (:count (first (q-map "SELECT count(*) FROM laskutusyhteenveto_cache c WHERE c.urakka = " urakka-id " AND c.alkupvm = '2015-8-01';")))

          ;; muuta yks.hintaa tehtävälle Metsän harvennus ko. ajalle --> cachen pitäisi tyhjentyä
          metsanharvennus-yksikkohinta-hyotykuorma [{:yhteensa-kkt-1-9 122040, :yhteensa 162720, :yhteensa-kkt-10-12 40680,
                                                     :loppupvm (pvm/->pvm "31.12.2014") :yksikko "ha", :koskematon true,
                                                     :tehtava 1432, :urakka 4, :yksikkohinta 678, :maara 600,
                                                     :tehtavan_nimi "Metsän harvennus", :sopimus 2, :maara-kkt-1-9 180,
                                                     :alkupvm (pvm/->pvm "1.10.2014") :tehtavan_id 1432}
                                                    {:yhteensa-kkt-1-9 122040, :yhteensa 162720, :yhteensa-kkt-10-12 40680,
                                                     :loppupvm (pvm/->pvm "30.09.2015") :yksikko "ha", :koskematon true,
                                                     :tehtava 1432, :urakka 4, :yksikkohinta 678, :maara 1800,
                                                     :tehtavan_nimi "Metsän harvennus", :sopimus 2, :maara-kkt-1-9 180,
                                                     :alkupvm (pvm/->pvm "1.1.2015") :tehtavan_id 1432}]]
      (is (= cache-count 1) "Monta kyselyä samoin parametrein, silti vain yksi cachessa")
      (is (nil? cache-tyhja) "cache tyhjä ennen kyselyn ajoa")
      (is (some? cachesta-haettu-kysely) "cache ei tyhjä ennen kyselyn ajoa")

      (is (= 1000.0M
             (:yht_laskutettu haetut-tiedot-oulu-liikenneympariston-hoito)
             (:yht_laskutettu cachesta-haettu-kysely)) ":yht_laskutettu laskutusyhteenvedossa")
      (is (= 3000.0M
             (:yht_laskutetaan haetut-tiedot-oulu-liikenneympariston-hoito)
             (:yht_laskutetaan cachesta-haettu-kysely)) ":yht_laskutetaan laskutusyhteenvedossa")
      (is (= 3820.11494252873560900000M
             (:kaikki_laskutettu haetut-tiedot-oulu-liikenneympariston-hoito)
             (:kaikki_laskutettu cachesta-haettu-kysely)) ":kaikki_laskutettu laskutusyhteenvedossa")
      (is (= 13103.44827586206880000M
             (:kaikki_laskutetaan haetut-tiedot-oulu-liikenneympariston-hoito)
             (:kaikki_laskutetaan cachesta-haettu-kysely)) ":kaikki_laskutetaan laskutusyhteenvedossa")
      (is (= 20.11494252873560900000M
             (:kaikki_laskutettu_ind_korotus haetut-tiedot-oulu-liikenneympariston-hoito)
             (:kaikki_laskutettu_ind_korotus cachesta-haettu-kysely)) ":kaikki_laskutettu_ind_korotus laskutusyhteenvedossa")
      (is (= 103.44827586206880000M
             (:kaikki_laskutetaan_ind_korotus haetut-tiedot-oulu-liikenneympariston-hoito)
             (:kaikki_laskutetaan_ind_korotus cachesta-haettu-kysely)) ":kaikki_laskutetaan_ind_korotus laskutusyhteenvedossa")
      (let [yksikkohintaa-muutettu
            (kutsu-palvelua (:http-palvelin jarjestelma)
                            :tallenna-urakan-yksikkohintaiset-tyot +kayttaja-jvh+
                            {:urakka-id @oulun-alueurakan-2014-2019-id
                             :sopimusnumero @oulun-alueurakan-2014-2019-paasopimuksen-id
                             :tyot metsanharvennus-yksikkohinta-hyotykuorma})
            cache-tyhja-koska-yht-trigger (first (q-map "SELECT * FROM laskutusyhteenveto_cache WHERE urakka = " urakka-id ";"))
            toka-tietojen-haku (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                                 (:db jarjestelma) +kayttaja-jvh+
                                 {:urakka-id urakka-id :alkupvm (pvm/->pvm "1.8.2015")
                                  :loppupvm (pvm/->pvm "31.8.2015")})
            toka-haetut-tiedot-oulu-liikenneympariston-hoito (first (filter #(= (:tuotekoodi %) +tuotekoodi-liikenneympariston-hoito+) toka-tietojen-haku))
            cachesta-haettu-kysely-triggerin-jalkeen (hae-cachesta-kentat urakka-id +tuotekoodi-liikenneympariston-hoito+) ]
        (is (nil? cache-tyhja-koska-yht-trigger) "cache-tyhja-koska-yht-trigger")
        (is (= 6780.0M
               (:yht_laskutettu toka-haetut-tiedot-oulu-liikenneympariston-hoito)
               (:yht_laskutettu cachesta-haettu-kysely-triggerin-jalkeen)) ":yht_laskutettu laskutusyhteenvedossa")
        (is (= 20340.0M
               (:yht_laskutetaan toka-haetut-tiedot-oulu-liikenneympariston-hoito)
               (:yht_laskutetaan cachesta-haettu-kysely-triggerin-jalkeen)) ":yht_laskutetaan laskutusyhteenvedossa")
        (is (= 9572.43295019157085802000M
               (:kaikki_laskutettu toka-haetut-tiedot-oulu-liikenneympariston-hoito)
               (:kaikki_laskutettu cachesta-haettu-kysely-triggerin-jalkeen)) ":kaikki_laskutettu laskutusyhteenvedossa")
        (is (= 30742.41379310344763200M
               (:kaikki_laskutetaan toka-haetut-tiedot-oulu-liikenneympariston-hoito)
               (:kaikki_laskutetaan cachesta-haettu-kysely-triggerin-jalkeen)) ":kaikki_laskutetaan laskutusyhteenvedossa")
        (is (= -7.56704980842914198000M
               (:kaikki_laskutettu_ind_korotus toka-haetut-tiedot-oulu-liikenneympariston-hoito)
               (:kaikki_laskutettu_ind_korotus cachesta-haettu-kysely-triggerin-jalkeen)) ":kaikki_laskutettu_ind_korotus laskutusyhteenvedossa")
        (is (= 402.41379310344763200M
               (:kaikki_laskutetaan_ind_korotus toka-haetut-tiedot-oulu-liikenneympariston-hoito)
               (:kaikki_laskutetaan_ind_korotus cachesta-haettu-kysely-triggerin-jalkeen)) ":kaikki_laskutetaan_ind_korotus laskutusyhteenvedossa")))))

(deftest laskutusyhteenvedon-cache-tyhjenee-jos-muutoshinta-muuttuu
  (testing "laskutusyhteenvedon-cache-tyhjenee-jos-muutoshinta-muuttuu"
    (let [urakka-id @oulun-alueurakan-2014-2019-id
          oulu-liik-ymp-hoito-tpi-id (hae-oulun-alueurakan-liikenneympariston-hoito-tpi-id)
          vesakonraivaus-tpk-id (ffirst (q "SELECT id FROM toimenpidekoodi WHERE nimi = 'Vesakonraivaus' AND taso = 4;"))
          oulu-vesakonraivaus-muutoshinta-id (ffirst (q "SELECT id FROM muutoshintainen_tyo WHERE urakka = " urakka-id
                                                       " AND sopimus = " @oulun-alueurakan-2014-2019-paasopimuksen-id
                                                       " AND tehtava = " vesakonraivaus-tpk-id ";"))
          varmista-tyhjyys (tyhjenna-urakan-cache urakka-id)
          cache-tyhja (first (q-map "SELECT * FROM laskutusyhteenveto_cache WHERE urakka = " urakka-id ";"))
          eka-tietojen-haku (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                              (:db jarjestelma) +kayttaja-jvh+
                              {:urakka-id urakka-id :alkupvm (pvm/->pvm "1.8.2015")
                               :loppupvm (pvm/->pvm "31.8.2015")})
          cachesta-haettu-kysely (hae-cachesta-kentat urakka-id +tuotekoodi-liikenneympariston-hoito+)
          haetut-tiedot-oulu-liikenneympariston-hoito (first (filter #(= (:tuotekoodi %) +tuotekoodi-liikenneympariston-hoito+) eka-tietojen-haku))
          cache-count (:count (first (q-map "SELECT count(*) FROM laskutusyhteenveto_cache c WHERE c.urakka = " urakka-id " AND c.alkupvm = '2015-8-01';")))

          ;; muuta yks.hintaa tehtävälle Metsän harvennus ko. ajalle --> cachen pitäisi tyhjentyä
          vesakonraivaus-muutoshinta-hyotykuorma
          [{:id oulu-vesakonraivaus-muutoshinta-id :urakka @oulun-alueurakan-2014-2019-id,
            :tehtava vesakonraivaus-tpk-id :tehtavanimi "Vesakonraivaus" :yksikko "ha"
            :alkupvm (pvm/->pvm-aika "1.10.2014 00:00:00.000") :loppupvm (pvm/->pvm-aika "30.9.2019 23:59:59.000")
            :yksikkohinta 2000, :toimenpideinstanssi oulu-liik-ymp-hoito-tpi-id :sopimus @oulun-alueurakan-2014-2019-paasopimuksen-id}]]
      (is (= cache-count 1) "Monta kyselyä samoin parametrein, silti vain yksi cachessa")
      (is (nil? cache-tyhja) "cache tyhjä ennen kyselyn ajoa")
      (is (some? cachesta-haettu-kysely) "cache ei tyhjä ennen kyselyn ajoa")

      (is (= 3000.0M
             (:muutostyot_laskutettu haetut-tiedot-oulu-liikenneympariston-hoito)
             (:muutostyot_laskutettu cachesta-haettu-kysely)) ":muutostyot_laskutettu laskutusyhteenvedossa")
      (is (= 7000.0M
             (:muutostyot_laskutetaan haetut-tiedot-oulu-liikenneympariston-hoito)
             (:muutostyot_laskutetaan cachesta-haettu-kysely)) ":muutostyot_laskutetaan laskutusyhteenvedossa")
      (is (= 3820.11494252873560900000M
             (:kaikki_laskutettu haetut-tiedot-oulu-liikenneympariston-hoito)
             (:kaikki_laskutettu cachesta-haettu-kysely)) ":kaikki_laskutettu laskutusyhteenvedossa")
      (is (= 13103.44827586206880000M
             (:kaikki_laskutetaan haetut-tiedot-oulu-liikenneympariston-hoito)
             (:kaikki_laskutetaan cachesta-haettu-kysely)) ":kaikki_laskutetaan laskutusyhteenvedossa")
      (is (= 20.11494252873560900000M
             (:kaikki_laskutettu_ind_korotus haetut-tiedot-oulu-liikenneympariston-hoito)
             (:kaikki_laskutettu_ind_korotus cachesta-haettu-kysely)) ":kaikki_laskutettu_ind_korotus laskutusyhteenvedossa")
      (is (= 103.44827586206880000M
             (:kaikki_laskutetaan_ind_korotus haetut-tiedot-oulu-liikenneympariston-hoito)
             (:kaikki_laskutetaan_ind_korotus cachesta-haettu-kysely)) ":kaikki_laskutetaan_ind_korotus laskutusyhteenvedossa")
      (let [yksikkohintaa-muutettu
            (kutsu-palvelua (:http-palvelin jarjestelma)
                            :tallenna-muutoshintaiset-tyot +kayttaja-jvh+
                            {:urakka-id @oulun-alueurakan-2014-2019-id
                             :tyot vesakonraivaus-muutoshinta-hyotykuorma})
            cache-tyhja-koska-yht-trigger (first (q-map "SELECT * FROM laskutusyhteenveto_cache WHERE urakka = " urakka-id ";"))
            toka-tietojen-haku (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                                 (:db jarjestelma) +kayttaja-jvh+
                                 {:urakka-id urakka-id :alkupvm (pvm/->pvm "1.8.2015")
                                  :loppupvm (pvm/->pvm "31.8.2015")})
            toka-haetut-tiedot-oulu-liikenneympariston-hoito (first (filter #(= (:tuotekoodi %) +tuotekoodi-liikenneympariston-hoito+) toka-tietojen-haku))
            cachesta-haettu-kysely-triggerin-jalkeen (hae-cachesta-kentat urakka-id +tuotekoodi-liikenneympariston-hoito+) ]
        (is (nil? cache-tyhja-koska-yht-trigger) "cache-tyhja-koska-yht-trigger")
        (is (= 22000.0M
               (:muutostyot_laskutettu toka-haetut-tiedot-oulu-liikenneympariston-hoito)
               (:muutostyot_laskutettu cachesta-haettu-kysely-triggerin-jalkeen)) ":muutostyot_laskutettu laskutusyhteenvedossa")
        (is (= 26000.0M
               (:muutostyot_laskutetaan toka-haetut-tiedot-oulu-liikenneympariston-hoito)
               (:muutostyot_laskutetaan cachesta-haettu-kysely-triggerin-jalkeen)) ":muutostyot_laskutetaan laskutusyhteenvedossa")
        (is (= 42366.09195402298760900000M
               (:kaikki_laskutettu toka-haetut-tiedot-oulu-liikenneympariston-hoito)
               (:kaikki_laskutettu cachesta-haettu-kysely-triggerin-jalkeen)) ":kaikki_laskutettu laskutusyhteenvedossa")
        (is (= 71086.2068965517224000M
               (:kaikki_laskutetaan toka-haetut-tiedot-oulu-liikenneympariston-hoito)
               (:kaikki_laskutetaan cachesta-haettu-kysely-triggerin-jalkeen)) ":kaikki_laskutetaan laskutusyhteenvedossa")
        (is (= 566.09195402298760900000M
               (:kaikki_laskutettu_ind_korotus toka-haetut-tiedot-oulu-liikenneympariston-hoito)
               (:kaikki_laskutettu_ind_korotus cachesta-haettu-kysely-triggerin-jalkeen)) ":kaikki_laskutettu_ind_korotus laskutusyhteenvedossa")
        (is (= 1086.2068965517224000M
               (:kaikki_laskutetaan_ind_korotus toka-haetut-tiedot-oulu-liikenneympariston-hoito)
               (:kaikki_laskutetaan_ind_korotus cachesta-haettu-kysely-triggerin-jalkeen)) ":kaikki_laskutetaan_ind_korotus laskutusyhteenvedossa")))))

(deftest laskutusyhteenvedon-cache-tyhjenee-jos-erilliskustannus-muuttuu
  (testing "laskutusyhteenvedon-cache-tyhjenee-jos-erilliskustannus-muuttuu"
    (let [urakka-id @oulun-alueurakan-2014-2019-id
          ek-summa 665
          varmista-tyhjyys (tyhjenna-urakan-cache urakka-id)
          cache-tyhja (first (q-map "SELECT * FROM laskutusyhteenveto_cache WHERE urakka = " urakka-id ";"))
          eka-tietojen-haku (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                              (:db jarjestelma) +kayttaja-jvh+
                              {:urakka-id urakka-id :alkupvm (pvm/->pvm "1.8.2015")
                               :loppupvm (pvm/->pvm "31.8.2015")})
          cachesta-haettu-kysely (hae-cachesta-kentat urakka-id +tuotekoodi-talvihoito+)
          haetut-tiedot-oulu-talvihoito (first (filter #(= (:tuotekoodi %) +tuotekoodi-talvihoito+) eka-tietojen-haku))
          cache-count (:count (first (q-map "SELECT count(*) FROM laskutusyhteenveto_cache c WHERE c.urakka = " urakka-id " AND c.alkupvm = '2015-8-01';")))

          ;; muuta yks.hintaa tehtävälle Metsän harvennus ko. ajalle --> cachen pitäisi tyhjentyä
          erilliskustannus-hyotykuorma
          {:pvm (pvm/->pvm "24.8.2015") :rahasumma ek-summa, :urakka-id @oulun-alueurakan-2014-2019-id,
           :loppupvm (pvm/->pvm "30.9.2015") :urakka @oulun-alueurakan-2014-2019-id, :maksaja :tilaaja, :indeksin_nimi "MAKU 2005"
           :toimenpideinstanssi (hae-oulun-alueurakan-talvihoito-tpi-id), :sopimus @oulun-alueurakan-2014-2019-paasopimuksen-id :alkupvm (pvm/->pvm "1.10.2014")
           :tyyppi "muu"}]
      (is (= cache-count 1) "Monta kyselyä samoin parametrein, silti vain yksi cachessa")
      (is (nil? cache-tyhja) "cache tyhjä ennen kyselyn ajoa")
      (is (some? cachesta-haettu-kysely) "cache ei tyhjä ennen kyselyn ajoa")

      (is (= 1000.0M
             (:erilliskustannukset_laskutettu haetut-tiedot-oulu-talvihoito)
             (:erilliskustannukset_laskutettu cachesta-haettu-kysely)) ":erilliskustannukset_laskutettu laskutusyhteenvedossa")
      (is (= 1000.0M
             (:erilliskustannukset_laskutetaan haetut-tiedot-oulu-talvihoito)
             (:erilliskustannukset_laskutetaan cachesta-haettu-kysely)) ":erilliskustannukset_laskutetaan laskutusyhteenvedossa")
      (is (= 39042.24137931034423443500M
             (:kaikki_laskutettu haetut-tiedot-oulu-talvihoito)
             (:kaikki_laskutettu cachesta-haettu-kysely)) ":kaikki_laskutettu laskutusyhteenvedossa")
      (is (= -23263.7867177522351753088000000M
             (:kaikki_laskutetaan haetut-tiedot-oulu-talvihoito)
             (:kaikki_laskutetaan cachesta-haettu-kysely)) ":kaikki_laskutetaan laskutusyhteenvedossa")
      (is (= 142.24137931034423443500M
             (:kaikki_laskutettu_ind_korotus haetut-tiedot-oulu-talvihoito)
             (:kaikki_laskutettu_ind_korotus cachesta-haettu-kysely)) ":kaikki_laskutettu_ind_korotus laskutusyhteenvedossa")
      (is (= -3.7867177522351753088000000M
             (:kaikki_laskutetaan_ind_korotus haetut-tiedot-oulu-talvihoito)
             (:kaikki_laskutetaan_ind_korotus cachesta-haettu-kysely)) ":kaikki_laskutetaan_ind_korotus laskutusyhteenvedossa")
      (let [erilliskustannus-lisatty
            (kutsu-palvelua (:http-palvelin jarjestelma)
                            :tallenna-erilliskustannus +kayttaja-jvh+
                            erilliskustannus-hyotykuorma)
            cache-tyhja-koska-yht-trigger (first (q-map "SELECT * FROM laskutusyhteenveto_cache WHERE urakka = " urakka-id ";"))
            toka-tietojen-haku (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                                 (:db jarjestelma) +kayttaja-jvh+
                                 {:urakka-id urakka-id :alkupvm (pvm/->pvm "1.8.2015")
                                  :loppupvm (pvm/->pvm "31.8.2015")})
            toka-haetut-tiedot-oulu-talvihoito (first (filter #(= (:tuotekoodi %) +tuotekoodi-talvihoito+) toka-tietojen-haku))
            cachesta-haettu-kysely-triggerin-jalkeen (hae-cachesta-kentat urakka-id +tuotekoodi-talvihoito+)]
        (is (nil? cache-tyhja-koska-yht-trigger) "cache-tyhja-koska-yht-trigger")
        (is (= 1000.0M
               (:erilliskustannukset_laskutettu toka-haetut-tiedot-oulu-talvihoito)
               (:erilliskustannukset_laskutettu cachesta-haettu-kysely-triggerin-jalkeen)) ":erilliskustannukset_laskutettu laskutusyhteenvedossa")
        (is (= 1665.0M                                      ;665e ek lisätty
               (+ ek-summa (:erilliskustannukset_laskutetaan haetut-tiedot-oulu-talvihoito))
               (:erilliskustannukset_laskutetaan toka-haetut-tiedot-oulu-talvihoito)
               (:erilliskustannukset_laskutetaan cachesta-haettu-kysely-triggerin-jalkeen)) ":erilliskustannukset_laskutetaan laskutusyhteenvedossa")
        (is (= 39042.24137931034423443500M
               (:kaikki_laskutettu toka-haetut-tiedot-oulu-talvihoito)
               (:kaikki_laskutettu cachesta-haettu-kysely-triggerin-jalkeen)) ":kaikki_laskutettu laskutusyhteenvedossa")
        (is (= -22587.3212005108558833088000000M
               (:kaikki_laskutetaan toka-haetut-tiedot-oulu-talvihoito)
               (:kaikki_laskutetaan cachesta-haettu-kysely-triggerin-jalkeen)) ":kaikki_laskutetaan laskutusyhteenvedossa")
        (is (= 142.24137931034423443500M
               (:kaikki_laskutettu_ind_korotus toka-haetut-tiedot-oulu-talvihoito)
               (:kaikki_laskutettu_ind_korotus cachesta-haettu-kysely-triggerin-jalkeen)) ":kaikki_laskutettu_ind_korotus laskutusyhteenvedossa")
        (is (= 7.6787994891441166912000000M
               (:kaikki_laskutetaan_ind_korotus toka-haetut-tiedot-oulu-talvihoito)
               (:kaikki_laskutetaan_ind_korotus cachesta-haettu-kysely-triggerin-jalkeen)) ":kaikki_laskutetaan_ind_korotus laskutusyhteenvedossa")))))


;; HAR-7520
(deftest laskutusyhteenvedon-cache-tyhjenee-jos-urakan-kayttama-indeksi-muuttuu
  (testing "laskutusyhteenvedon-cache-tyhjenee-jos-urakan-kayttama-indeksi-muuttuu"
    (let [urakka-id @oulun-alueurakan-2014-2019-id
          varmista-tyhjyys (tyhjenna-urakan-cache urakka-id)
          cache-tyhja (first (q-map "SELECT * FROM laskutusyhteenveto_cache WHERE urakka = " urakka-id ";"))
          eka-tietojen-haku (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                              (:db jarjestelma) +kayttaja-jvh+
                              {:urakka-id urakka-id :alkupvm (pvm/->pvm "1.8.2015")
                               :loppupvm (pvm/->pvm "31.8.2015")})
          cachesta-haettu-kysely (hae-cachesta-kentat urakka-id +tuotekoodi-talvihoito+)
          cache-count (:count (first (q-map "SELECT count(*) FROM laskutusyhteenveto_cache c WHERE c.urakka = " urakka-id " AND c.alkupvm = '2015-8-01';")))
          indeksi-ennen-maku2005 (ffirst (q "SELECT indeksi FROM  urakka where id = " urakka-id))
          ;; muuta Oulun urakan käyttämää indeksiä
          poistettu-indeksi (u "UPDATE urakka SET indeksi = NULL where id = " urakka-id)
          indeksi-jalkeen-maku2010 (ffirst (q "SELECT indeksi FROM  urakka where id = " urakka-id))
          cache-count-indeksipaivityksen-jalkeen (:count (first (q-map "SELECT count(*) FROM laskutusyhteenveto_cache c WHERE c.urakka = " urakka-id " AND c.alkupvm = '2015-8-01';")))]
      (is (= cache-count 1) "Monta kyselyä samoin parametrein, silti vain yksi cachessa")
      (is (nil? cache-tyhja) "cache tyhjä ennen kyselyn ajoa")
      (is (some? cachesta-haettu-kysely) "cache ei tyhjä ennen kyselyn ajoa")
      (is (= "MAKU 2005" indeksi-ennen-maku2005) "indeksi ennen muutosta")
      (is (nil? indeksi-jalkeen-maku2010) "indeksi jälkeen muutoksen")

      ;; varmista että cache tyhjenee kun indeksiä muutetaan
      (is (= 0 cache-count-indeksipaivityksen-jalkeen)))))

;; Kysely, jolla voi tarkistaa onko tyhjiä laskutusyhteenvetoja:
;; SELECT u.nimi, y.urakka, y.alkupvm, y.loppupvm, y.tallennettu,
;;        SUM( (y.rivi).kaikki_laskutetaan ) as kaikki_laskutetaan_summa
;;   FROM (SELECT urakka, alkupvm, loppupvm, tallennettu, unnest(rivit) as rivi
;;           FROM laskutusyhteenveto_cache) y
;;        JOIN urakka u ON u.id=y.urakka
;;  WHERE y.alkupvm >= '2016-10-01' AND
;;        y.loppupvm < NOW()
;; GROUP BY u.nimi,y.urakka,y.alkupvm,y.loppupvm,y.tallennettu
;; HAVING SUM( (y.rivi).kaikki_laskutetaan ) = 0;
