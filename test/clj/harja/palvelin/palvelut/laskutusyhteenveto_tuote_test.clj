(ns harja.palvelin.palvelut.laskutusyhteenveto-tuote-test
  (:require [clojure.test :refer :all]
            [harja.kyselyt.tehtavamaarat :as tehtavamaarat-kyselyt]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as lyv-yhteiset]
            [harja.palvelin.palvelut.yksikkohintaiset-tyot :refer :all]
            [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
            [harja.kyselyt.kulut :as kulut-q]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]))


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


(def hallinnolliset-toimenpiteet-tpi-id
  (ffirst (q (str "SELECT id from toimenpideinstanssi where nimi = 'Oulu MHU Hallinnolliset toimenpiteet TP'"))))

(defn hae-hallinnollisten-toimenpiteiden-tpi-id [urakka-id]
  (:id (first (q-map (format "SELECT tpi.id
                               FROM toimenpideinstanssi tpi
                               JOIN toimenpide tpk3 ON tpk3.id = tpi.toimenpide
                               JOIN toimenpide tpk2 ON tpk3.emo = tpk2.id
                              WHERE tpi.urakka = %s
                                AND tpk2.koodi = '23150'
                              LIMIT 1" urakka-id)))))


;; Varmistetaan, että laskutusyhteenveton ja suolasakkoon liittyvä bugi onn korjaantunut:
;; Laskutusyhteenveto ottaa talvisuolasakon väärään hoitokauteen loka-joulukuussa
(deftest suolasakko-oikean-vuoden-laskutusyhteenvedossa
  (testing "suolasakko-oikean-vuoden-laskutusyhteenvedossa"
    (let [haetut-tiedot-oulu (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                               (:db jarjestelma)
                               +kayttaja-jvh+
                               {:urakka-id @oulun-alueurakan-2014-2019-id
                                :alkupvm (pvm/->pvm "1.10.2014")
                                :loppupvm (pvm/->pvm "31.10.2014")})
          haetut-tiedot-oulu-talvihoito (first (filter #(= (:tuotekoodi %) "23100") haetut-tiedot-oulu))]

      (is (= (:suolasakko_kaytossa haetut-tiedot-oulu-talvihoito) true) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutettu haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutettu_ind_korotettuna haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutettu_ind_korotus haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutetaan haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutetaan_ind_korotettuna haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutetaan_ind_korotus haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa"))))

(deftest suolasakko-oikein-hoitokauden-laskutusyhteenvedossa
  (testing "suolasakko-oikein-hoitokauden-laskutusyhteenvedossa"
    (let [haetut-tiedot-oulu (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                               (:db jarjestelma)
                               +kayttaja-jvh+
                               {:urakka-id @oulun-alueurakan-2014-2019-id
                                :alkupvm (pvm/->pvm "1.10.2014")
                                :loppupvm (pvm/->pvm "30.9.2015")})
          haetut-tiedot-oulu-talvihoito (first (filter #(= (:tuotekoodi %) "23100") haetut-tiedot-oulu))]

      (is (= (:suolasakko_kaytossa haetut-tiedot-oulu-talvihoito) true) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutettu haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutettu_ind_korotettuna haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (= (:suolasakot_laskutettu_ind_korotus haetut-tiedot-oulu-talvihoito) 0.0M) "suolasakko laskutusyhteenvedossa")
      (is (=marginaalissa? (:suolasakot_laskutetaan haetut-tiedot-oulu-talvihoito) -29760.0M) "suolasakko laskutusyhteenvedossa")
      (is (=marginaalissa? (:suolasakot_laskutetaan_ind_korotettuna haetut-tiedot-oulu-talvihoito) -29864.5M) "suolasakko laskutusyhteenvedossa")
      (is (=marginaalissa? (:suolasakot_laskutetaan_ind_korotus haetut-tiedot-oulu-talvihoito) -104.5M) "suolasakko laskutusyhteenvedossa"))))


(deftest kuun-viimeisen-paivan-yht-oikein-laskutusyhteenvedossa
  (testing "kuun-viimeisen-paivan-yht-oikein-laskutusyhteenvedossa"
    (let [haetut-tiedot-oulu (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
                               (:db jarjestelma)
                               +kayttaja-jvh+
                               {:urakka-id @oulun-alueurakan-2014-2019-id
                                :alkupvm (pvm/->pvm "1.11.2016")
                                :loppupvm (pvm/->pvm "30.11.2016")})
          haetut-tiedot-oulu-liikenneympariston-hoito (first (filter #(= (:tuotekoodi %) "23110") haetut-tiedot-oulu))]
      (println " haetut tiedot liikenne" (select-keys haetut-tiedot-oulu-liikenneympariston-hoito
                                           [:yht_laskutetaan :yht_laskutetaan_ind_korotus :yht_laskutetaan_ind_korotettuna]))

      (is (= (:yht_laskutetaan haetut-tiedot-oulu-liikenneympariston-hoito) 7882.5M) ":yht_laskutetaan laskutusyhteenvedossa")
      (is (= (:yht_laskutetaan_ind_korotus haetut-tiedot-oulu-liikenneympariston-hoito) 2310.387931034483003250M) ":yht_laskutetaan laskutusyhteenvedossa")
      (is (= (:yht_laskutetaan_ind_korotettuna haetut-tiedot-oulu-liikenneympariston-hoito) 10192.887931034483003250M) ":yht_laskutetaan laskutusyhteenvedossa"))))


(deftest tyomaakokous-laskutusyhteenvedossa-sanktio-loytyy-sanktiot-kentasta
  (testing "Työmaakokouksen laskutusyhteenvedossa tavallinen sanktio löytyy sanktiot-kentästä"
    (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
          testipvm "2026-11-15"
          alkupvm (pvm/->pvm "1.10.2026")
          loppupvm (pvm/->pvm "30.11.2026")
          sanktiomaara 777
          odotettu-summa -777.0M
          sanktiotyyppi-id (:id (first (q-map "SELECT id FROM sanktiotyyppi WHERE nimi = 'Hallinnolliset laiminlyönnit'")))

          _ (u (format "DELETE FROM sanktio WHERE toimenpideinstanssi = %s AND perintapvm = '%s'::DATE"
                 hallinnolliset-toimenpiteet-tpi-id testipvm))
          _ (lisaa-suorasanktio-urakalle sanktiomaara "vaihtosanktio" testipvm urakka-id
               hallinnolliset-toimenpiteet-tpi-id sanktiotyyppi-id nil nil)

          tiedot (lyv-yhteiset/hae-tyomaa-laskutusyhteenvedon-tiedot
                   (:db jarjestelma)
                   +kayttaja-jvh+
                   {:urakka-id urakka-id
                    :alkupvm alkupvm
                    :loppupvm loppupvm})
          rivi (first tiedot)]

      (is (= odotettu-summa (:sanktiot_val_aika_yht rivi))
          "Tavallinen sanktio löytyy sanktiot_val_aika_yht-kentästä")
      (is (= odotettu-summa (:sanktiot_hoitokausi_yht rivi))
          "Tavallinen sanktio löytyy sanktiot_hoitokausi_yht-kentästä")
      (is (or (nil? (:arvonvahennykset_val_aika_yht rivi))
              (= 0M (:arvonvahennykset_val_aika_yht rivi)))
          "Arvonvähennykset ovat tyhjät, kun niitä ei ole lisätty")

      (u (format "DELETE FROM sanktio WHERE toimenpideinstanssi = %s AND perintapvm = '%s'::DATE"
           hallinnolliset-toimenpiteet-tpi-id testipvm)))))


(deftest tyomaakokous-laskutusyhteenvedossa-arvonvahennys-erotellaan-sanktiosta
  (testing "Työmaakokouksen laskutusyhteenvedossa arvonvähennys erotellaan sanktiosta hoitovuonna 2026"
    (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
          testipvm "2026-12-10"
          alkupvm (pvm/->pvm "1.10.2026")
          loppupvm (pvm/->pvm "31.12.2026")
          sanktiomaara 333
          arvonvahennysmaara 222
          odotettu-sanktio -333.0M
          odotettu-arvonvahennys -222M
          sanktiotyyppi-id (:id (first (q-map "SELECT id FROM sanktiotyyppi WHERE nimi = 'Hallinnolliset laiminlyönnit'")))

          _ (u (format "DELETE FROM sanktio WHERE toimenpideinstanssi = %s AND perintapvm = '%s'::DATE"
                 hallinnolliset-toimenpiteet-tpi-id testipvm))
          _ (lisaa-suorasanktio-urakalle sanktiomaara "vaihtosanktio" testipvm urakka-id
               hallinnolliset-toimenpiteet-tpi-id sanktiotyyppi-id nil nil)
          _ (lisaa-suorasanktio-urakalle arvonvahennysmaara "arvonvahennyssanktio" testipvm urakka-id
               hallinnolliset-toimenpiteet-tpi-id sanktiotyyppi-id nil nil)

          tiedot (lyv-yhteiset/hae-tyomaa-laskutusyhteenvedon-tiedot
                   (:db jarjestelma)
                   +kayttaja-jvh+
                   {:urakka-id urakka-id
                    :alkupvm alkupvm
                    :loppupvm loppupvm})
          rivi (first tiedot)]

      (is (= odotettu-sanktio (:sanktiot_val_aika_yht rivi))
          "Tavallinen sanktio löytyy sanktiot_val_aika_yht-kentästä")
      (is (= odotettu-sanktio (:sanktiot_hoitokausi_yht rivi))
          "Tavallinen sanktio löytyy sanktiot_hoitokausi_yht-kentästä")
      (is (= odotettu-arvonvahennys (:arvonvahennykset_val_aika_yht rivi))
          "Arvonvähennys löytyy arvonvahennykset_val_aika_yht-kentästä")
      (is (= odotettu-arvonvahennys (:arvonvahennykset_hoitokausi_yht rivi))
          "Arvonvähennys löytyy arvonvahennykset_hoitokausi_yht-kentästä")

      (u (format "DELETE FROM sanktio WHERE toimenpideinstanssi = %s AND perintapvm = '%s'::DATE"
           hallinnolliset-toimenpiteet-tpi-id testipvm)))))


(deftest tuote-laskutusyhteenvedossa-arvonvahennys-on-omassa-osiossa-mhu25
  (testing "Tuote laskutusyhteenvedossa arvonvähennyssanktio näkyy omassa osiossa 2025+ urakalla"
    (let [urakka-id @kajaanin-maanteiden-hoitourakan-2025-2030-id
          urakan-alkuvuosi 2025
          hallinnollinen-tpi-id (hae-hallinnollisten-toimenpiteiden-tpi-id urakka-id)
          testipvm "2025-12-12"
          alkupvm (pvm/->pvm "1.10.2025")
          loppupvm (pvm/->pvm "30.09.2026")
          sanktiomaara 333
          arvonvahennysmaara 222
          odotettu-sanktio -333.0M
          odotettu-arvonvahennys -222M
          styyppi-laiminlyonti-id (:id (first (q-map "SELECT id FROM sanktiotyyppi WHERE nimi = 'Hallinnolliset laiminlyönnit'")))
          styyppi-arvonvah-id (:id (first (q-map "SELECT id FROM sanktiotyyppi WHERE nimi = 'Ei tarvita sanktiotyyppiä'")))
          kaikki-tehtavaryhmat (kulut-q/hae-kaikkien-tehtavaryhmien-nimet (:db jarjestelma))
          talvisuola-b (first (filter #(= (:tehtavaryhma_nimi %) "B1 - Talvisuola") kaikki-tehtavaryhmat))
          tehtavaryhman-tehtava (first (tehtavamaarat-kyselyt/tehtavaryhman-tehtavat-urakalle (:db jarjestelma)
                                   {:urakka-id urakka-id
                                    :tehtavaryhma-id (:tehtavaryhma talvisuola-b)
                                    :urakan-alkuvuosi urakan-alkuvuosi}))
          ;; HAetaan toimenpideinstanssi toimenpid_id:llä
          tehtavaryhman-tpi (:id (first (q-map (format "SELECT id FROM toimenpideinstanssi WHERE urakka = %s AND toimenpide = %s" urakka-id (:tehtavaryhma_toimenpide_id talvisuola-b)))))

          _ (u (format "DELETE FROM sanktio WHERE toimenpideinstanssi = %s AND perintapvm = '%s'::DATE"
                 hallinnollinen-tpi-id testipvm))
             tuotekohtainen-laskutusyhteenveto-ennen
             (laskutus-q/hae-laskutusyhteenvedon-tiedot-tuotekohtainen (:db jarjestelma)
               {:urakka (int urakka-id)
                :hk_alkupvm alkupvm
                :hk_loppupvm loppupvm
                :aikavali_alkupvm alkupvm
                :aikavali_loppupvm loppupvm})
          _ (lisaa-suorasanktio-urakalle sanktiomaara "vaihtosanktio" testipvm urakka-id
              hallinnollinen-tpi-id styyppi-laiminlyonti-id nil nil)
          _ (lisaa-suorasanktio-urakalle arvonvahennysmaara "arvonvahennyssanktio" testipvm urakka-id
              tehtavaryhman-tpi styyppi-arvonvah-id (:tehtavaryhma talvisuola-b) (:id tehtavaryhman-tehtava))
          tuotekohtainen-laskutusyhteenveto
          (laskutus-q/hae-laskutusyhteenvedon-tiedot-tuotekohtainen (:db jarjestelma)
            {:urakka (int urakka-id)
             :hk_alkupvm alkupvm
             :hk_loppupvm loppupvm
             :aikavali_alkupvm alkupvm
             :aikavali_loppupvm loppupvm})
          ;; MHU ja HJU hoidon johto - tuotteen rivi
            hoidonjohto-rivi-ennen (first (filter #(= (:nimi %) "MHU ja HJU hoidon johto")
                              tuotekohtainen-laskutusyhteenveto-ennen))
          hoidonjohto-rivi (first (filter #(= (:nimi %) "MHU ja HJU hoidon johto") tuotekohtainen-laskutusyhteenveto))
          ;; Haetaan talvihoito riviltä, koska tehtäväryhmänä on b1-talvihoito
            talvihoito-rivi-ennen (first (filter #(= (:nimi %) "Talvihoito")
                               tuotekohtainen-laskutusyhteenveto-ennen))
          talvihoito-rivi (first (filter #(= (:nimi %) "Talvihoito") tuotekohtainen-laskutusyhteenveto))
            hoidonjohto-rivi-ennen (into (sorted-map) hoidonjohto-rivi-ennen)
          hoidonjohto-rivi (into (sorted-map) hoidonjohto-rivi)
            talvihoito-rivi-ennen (into (sorted-map) talvihoito-rivi-ennen)
          talvihoito-rivi (into (sorted-map) talvihoito-rivi)]

          (is (= odotettu-sanktio
             (- (:sakot_laskutetaan hoidonjohto-rivi)
              (:sakot_laskutetaan hoidonjohto-rivi-ennen)))
            "Lisätty tavallinen sanktio kuuluu ::sakot_laskutetaan-kenttään")
          (is (= odotettu-arvonvahennys
             (- (or (:arvonvahennykset_laskutetaan talvihoito-rivi) 0M)
              (or (:arvonvahennykset_laskutetaan talvihoito-rivi-ennen) 0M)))
            "Lisätty arvonvähennys kuuluu arvonvahennykset_laskutetaan-kenttään")

          (u (format "DELETE FROM sanktio WHERE toimenpideinstanssi IN (%s, %s) AND perintapvm = '%s'::DATE"
               hallinnollinen-tpi-id tehtavaryhman-tpi testipvm)))))


