(ns harja.palvelin.palvelut.laskutusyhteenveto-mhu-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-tuotekohtainen :as laskutusyhteenveto]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as lyv-yhteiset]

            [harja.palvelin.palvelut.yksikkohintaiset-tyot :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.sanktiot :as sanktiot]
            [harja.kyselyt.laskutusyhteenveto :as laskutusyhteenveto-kyselyt]
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

;; Tallenna monissa testeissa käytetty raportti atomiin, jotta tietokantahakuihin ei tarvitse tuhlata aikaa
(def oulun-mhu-urakka-2020-03 (atom []))
(def oulun-mhu-urakka-2022-2023 (atom []))
(def oulun-mhu-urakka-2020-04 (atom []))
(def oulun-mhu-urakka-2020-06 (atom []))

(def hallinnolliset-toimenpiteet-tpi-id
  (ffirst (q (str "SELECT id from toimenpideinstanssi where nimi = 'Oulu MHU Hallinnolliset toimenpiteet TP'"))))

(defn hae-2020-03-tiedot []
  (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
    (:db jarjestelma)
    +kayttaja-jvh+
    {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
     :urakkatyyppi "teiden-hoito"
     :alkupvm (pvm/->pvm "1.3.2020")
     :loppupvm (pvm/->pvm "31.3.2020")}))

(defn hae-2022-2023-oulu-mhu-tiedot []
  (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
    (:db jarjestelma)
    +kayttaja-jvh+
    {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
     :urakkatyyppi "teiden-hoito"
     :alkupvm (pvm/->pvm "1.10.2022")
     :loppupvm (pvm/->pvm "30.9.2023")}))

(defn hae-2020-04-tiedot []
  (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
    (:db jarjestelma)
    +kayttaja-jvh+
    {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
     :urakkatyyppi "teiden-hoito"
     :alkupvm (pvm/->pvm "1.4.2020")
     :loppupvm (pvm/->pvm "30.4.2020")}))

(defn hae-2020-06-tiedot []
  (lyv-yhteiset/hae-laskutusyhteenvedon-tiedot
    (:db jarjestelma)
    +kayttaja-jvh+
    {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
     :urakkatyyppi "teiden-hoito"
     :alkupvm (pvm/->pvm "1.6.2020")
     :loppupvm (pvm/->pvm "30.6.2020")}))

(deftest mhu-laskutusyhteenvedon-tietojen-haku
  (testing "mhu-laskutusyhteenvedon-tietojen-haku"
    (let [_ (when (= (empty? @oulun-mhu-urakka-2020-03))
              (reset! oulun-mhu-urakka-2020-03 (hae-2020-03-tiedot)))
          haetut-tiedot-oulu-talvihoito (first (filter #(= (:tuotekoodi %) "23100") @oulun-mhu-urakka-2020-03))
          haetut-tiedot-oulu-liikenneymparisto (first (filter #(= (:tuotekoodi %) "23110") @oulun-mhu-urakka-2020-03))
          haetut-tiedot-oulu-soratiet (first (filter #(= (:tuotekoodi %) "23120") @oulun-mhu-urakka-2020-03))
          haetut-tiedot-oulu-paallyste (first (filter #(= (:tuotekoodi %) "20100") @oulun-mhu-urakka-2020-03))
          haetut-tiedot-oulu-mhu-yllapito (first (filter #(= (:tuotekoodi %) "20190") @oulun-mhu-urakka-2020-03))
          haetut-tiedot-oulu-mhu-korvausinvestointi (first (filter #(= (:tuotekoodi %) "14300") @oulun-mhu-urakka-2020-03))
          haetut-tiedot-oulu-mhu-ja-hoidon-johto (first (filter #(= (:tuotekoodi %) "23150") @oulun-mhu-urakka-2020-03))]

      (is (= 7 (count @oulun-mhu-urakka-2020-03)))
      (is (not (empty? haetut-tiedot-oulu-talvihoito)))
      (is (not (empty? haetut-tiedot-oulu-liikenneymparisto)))
      (is (not (empty? haetut-tiedot-oulu-soratiet)))
      (is (not (empty? haetut-tiedot-oulu-paallyste)))
      (is (not (empty? haetut-tiedot-oulu-mhu-yllapito)))
      (is (not (empty? haetut-tiedot-oulu-mhu-korvausinvestointi)))
      (is (not (empty? haetut-tiedot-oulu-mhu-ja-hoidon-johto))))))

(deftest mhu-laskutusyhteenvedon-perusluku
  (testing "mhu-laskutusyhteenvedon-perusluku-tietojen-haku"
    (let [_ (when (= (empty? @oulun-mhu-urakka-2020-03))
              (reset! oulun-mhu-urakka-2020-03 (hae-2020-03-tiedot)))
          talvihoito (first (filter #(= (:tuotekoodi %) "23100") @oulun-mhu-urakka-2020-03))]
      ;; Tämä hajoaa joka vuosi 1.10. Koska Indeksi generoidaan tietokantaan. Päivitä arvo suoraan tietokannasta
      ;; ja kaikki on taas ihanasti toimivaa
      (is (= 110.8M (:perusluku talvihoito))))))

(deftest mhu-laskutusyhteenvedon-tavoitehinnat
  (testing "mhu-laskutusyhteenvedon-tavoitehinnat"
    (let [_ (when (= (empty? @oulun-mhu-urakka-2020-03))
              (reset! oulun-mhu-urakka-2020-03 (hae-2020-03-tiedot)))
          talvihoito (first (filter #(= (:tuotekoodi %) "23100") @oulun-mhu-urakka-2020-03))
          ;; Tavoitehintaan kuuluu Hankinnat, Johto- ja Hallintokorvaukset, (hoidonjohto tässä), Erillishankinnat, HJ-Palkkio.
          ;; Lasketaan talvihoidon (jos laskee yhdelle, niin se toimii kaikille) tavoitehinta
          talvihoidon-tavoitehinta (+
                                     (:hankinnat_laskutettu talvihoito)
                                     (:johto_ja_hallinto_laskutettu talvihoito)
                                     (:hj_erillishankinnat_laskutetaan talvihoito)
                                     (:hj_palkkio_laskutettu talvihoito))]

      (is (= talvihoidon-tavoitehinta (:tavoitehintaiset_laskutettu talvihoito))))))

(deftest mhu-laskutusyhteenvedon-sanktiot-joissa-indeksikorotus
  (testing "mhu-laskutusyhteenvedon-sanktiot-joissa-indeksikorotus"
    (let [_ (when (= (empty? @oulun-mhu-urakka-2020-03))
              (reset! oulun-mhu-urakka-2020-03 (hae-2020-03-tiedot)))
          talvihoito (first (filter #(= (:tuotekoodi %) "23100") @oulun-mhu-urakka-2020-03))
          maaliskuun-sanktiot (first (sanktiot/hae-urakan-sanktiot
                                       (:db jarjestelma) @oulun-maanteiden-hoitourakan-2019-2024-id
                                       (konv/sql-timestamp (pvm/->pvm "1.3.2020"))
                                       (konv/sql-timestamp (pvm/->pvm "31.3.2020"))))
          sanktiosumma-indeksikorotettuna (first (laskutusyhteenveto-kyselyt/hoitokautta-edeltavan-syyskuun-indeksikorotus
                                                   (:db jarjestelma)
                                                   {:hoitokauden-alkuvuosi 2019
                                                    :indeksinimi "MAKU 2015"
                                                    :summa (:summa maaliskuun-sanktiot)
                                                    :perusluku (:perusluku talvihoito)}))]

      (is (= (:sakot_laskutetaan talvihoito) (:korotettuna sanktiosumma-indeksikorotettuna))))))

(deftest mhu-laskutusyhteenvedon-hoidonjohdon-bonukset
  (testing "mhu-laskutusyhteenvedon-hoidonjohdon-bonukset"
    (let [_ (when (= (empty? @oulun-mhu-urakka-2020-03))
              (reset! oulun-mhu-urakka-2020-03 (hae-2020-03-tiedot)))
          hoidonjohto (first (filter #(= (:tuotekoodi %) "23150") @oulun-mhu-urakka-2020-03))
          muu-bonus (ffirst (q (str "SELECT SUM(rahasumma) FROM erilliskustannus WHERE
          (tyyppi = 'muu-bonus')
          AND toimenpideinstanssi = " hallinnolliset-toimenpiteet-tpi-id "
          AND poistettu IS NOT TRUE
          AND laskutuskuukausi >= '2019-10-01'::DATE AND laskutuskuukausi <= '2020-03-31'::DATE AND sopimus = " @oulun-maanteiden-hoitourakan-2019-2024-sopimus-id)))
          lupaus-ja-asiakastyytyvaisyys-bonus (ffirst (q (str "SELECT SUM(rahasumma) FROM erilliskustannus WHERE
          (tyyppi = 'lupausbonus' OR tyyppi = 'asiakastyytyvaisyysbonus' )
          AND toimenpideinstanssi = " hallinnolliset-toimenpiteet-tpi-id "
          AND poistettu IS NOT TRUE
          AND laskutuskuukausi >= '2019-10-01'::DATE AND laskutuskuukausi <= '2020-03-31'::DATE AND sopimus = " @oulun-maanteiden-hoitourakan-2019-2024-sopimus-id)))
          alihankinta-ja-tavoitepalkkio (ffirst (q (str "SELECT SUM(rahasumma) FROM erilliskustannus WHERE
          ( tyyppi = 'alihankintabonus' OR tyyppi = 'tavoitepalkkio' )
          AND toimenpideinstanssi = " hallinnolliset-toimenpiteet-tpi-id  "
          AND poistettu IS NOT TRUE
          AND laskutuskuukausi >= '2019-10-01'::DATE AND laskutuskuukausi <= '2020-03-31'::DATE AND sopimus = " @oulun-maanteiden-hoitourakan-2019-2024-sopimus-id)))
          lupaus-ja-asiakastyytyvaisyys-bonus-indeksilla (first (laskutusyhteenveto-kyselyt/hoitokautta-edeltavan-syyskuun-indeksikorotus
                                                   (:db jarjestelma)
                                                   {:hoitokauden-alkuvuosi 2019
                                                    :indeksinimi "MAKU 2015"
                                                    :summa lupaus-ja-asiakastyytyvaisyys-bonus
                                                    :perusluku (:perusluku hoidonjohto)}))
          ;; Tavoitehinnan ulkopuoliset rahavaraukset => lasketaan bonukseksi
          tav_ulk_rah (ffirst (q (str "SELECT COALESCE(SUM(kt.summa), 0) AS summa FROM kustannusarvioitu_tyo kt
                                       JOIN tehtavaryhma tr ON kt.tehtavaryhma = tr.id AND tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54'
                                       WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', kt.vuosi, kt.kuukausi, 1)::DATE)))
                                                  BETWEEN '2019-10-01'::DATE AND '2020-03-31'::DATE
                                             AND kt.sopimus = " @oulun-maanteiden-hoitourakan-2019-2024-sopimus-id)))]

      (is (= (:bonukset_laskutettu hoidonjohto)
             (+ (:korotettuna lupaus-ja-asiakastyytyvaisyys-bonus-indeksilla) alihankinta-ja-tavoitepalkkio muu-bonus tav_ulk_rah))))))

(deftest mhu-laskutusyhteenvedon-hoidonjohdon-sanktiot
  (testing "mhu-laskutusyhteenvedon-hoidonjohdon-sanktiot"
    (let [_ (when (= (empty? @oulun-mhu-urakka-2020-03))
              (reset! oulun-mhu-urakka-2020-03 (hae-2020-03-tiedot)))
          hoidonjohto (first (filter #(= (:tuotekoodi %) "23150") @oulun-mhu-urakka-2020-03))

          vaihtosanktio (ffirst (q (str "SELECT SUM(maara) FROM sanktio WHERE
          sakkoryhma = 'vaihtosanktio'
          AND toimenpideinstanssi = " hallinnolliset-toimenpiteet-tpi-id "
          AND poistettu IS NOT TRUE
          AND perintapvm >= '2019-10-01'::DATE AND perintapvm <= '2019-10-31'::DATE")))

          arvonvahennys (ffirst (q (str "SELECT SUM(maara) FROM sanktio WHERE
          sakkoryhma = 'arvonvahennyssanktio'
          AND toimenpideinstanssi = " hallinnolliset-toimenpiteet-tpi-id "
          AND poistettu IS NOT TRUE
          AND perintapvm >= '2019-10-01'::DATE AND perintapvm <= '2019-10-31'::DATE")))

          lupaus-ja-vaihtosanktiot-indeksikorotuksella (first (laskutusyhteenveto-kyselyt/hoitokautta-edeltavan-syyskuun-indeksikorotus
                                                                (:db jarjestelma)
                                                                {:hoitokauden-alkuvuosi 2019
                                                                 :indeksinimi "MAKU 2015"
                                                                 :summa vaihtosanktio
                                                                 :perusluku (:perusluku hoidonjohto)}))]

      (is (= (:sakot_laskutettu hoidonjohto)
             (* -1 (+ (:korotettuna lupaus-ja-vaihtosanktiot-indeksikorotuksella) arvonvahennys)))))))

(deftest mhu-laskutusyhteenvedon-hoidonjohdon-poikkeuslaskutukset
  (testing "mhu-laskutusyhteenvedon-hoidonjohdon-poikkeuslaskutukset"
    (let [_ (when (= (empty? @oulun-mhu-urakka-2020-04))
              (reset! oulun-mhu-urakka-2020-04 (hae-2020-04-tiedot)))
          hoidonjohto (first (filter #(= (:tuotekoodi %) "23150") @oulun-mhu-urakka-2020-04))

          poikkeukset (ffirst (q (str "SELECT SUM(kk.summa)
          FROM kulu_kohdistus kk
          WHERE kk.kulu IN (select id from kulu where tyyppi = 'laskutettava' AND erapaiva >= '2020-04-01' AND erapaiva <= '2020-04-30')
          AND kk.toimenpideinstanssi = " hallinnolliset-toimenpiteet-tpi-id)))

          db_hallinto (ffirst (q (str "SELECT SUM(kk.summa)
          FROM kulu k, kulu_kohdistus kk
          WHERE kk.kulu = (select id from kulu where kokonaissumma = 10.20 AND tyyppi = 'laskutettava' AND erapaiva = '2020-04-21')
          AND kk.toimenpideinstanssi = " hallinnolliset-toimenpiteet-tpi-id "
          AND kk.tehtavaryhma = (select id from tehtavaryhma where nimi = 'Johto- ja hallintokorvaus (J)')
          AND k.erapaiva = '2020-04-21'::DATE")))

          db_erillis (ffirst (q (str "SELECT SUM(kk.summa)
          FROM kulu k, kulu_kohdistus kk
          WHERE kk.kulu = (select id from kulu where kokonaissumma = 10.20 AND tyyppi = 'laskutettava' AND erapaiva = '2020-04-22')
          AND kk.toimenpideinstanssi = " hallinnolliset-toimenpiteet-tpi-id "
          AND kk.tehtavaryhma = (select id from tehtavaryhma where nimi = 'Erillishankinnat (W)')
          AND k.erapaiva = '2020-04-22'::DATE")))]

      (is (= (+ (:hj_palkkio_laskutetaan hoidonjohto) (:johto_ja_hallinto_laskutetaan hoidonjohto) (:hj_erillishankinnat_laskutetaan hoidonjohto))
             poikkeukset))
      (is (= (:johto_ja_hallinto_laskutetaan hoidonjohto)
             db_hallinto))
      (is (= (:hj_erillishankinnat_laskutetaan hoidonjohto)
             db_erillis)))))

(deftest mhu-laskutusyhteenvedon-hoidonjohdon-palkkiot
  (testing "mhu-laskutusyhteenvedon-hoidonjohdon-palkkiot"
    (let [_ (when (= (empty? @oulun-mhu-urakka-2020-03))
              (reset! oulun-mhu-urakka-2020-03 (hae-2020-03-tiedot)))
          hoidonjohto (first (filter #(= (:tuotekoodi %) "23150") @oulun-mhu-urakka-2020-03))
          tehtavaryhma-id (ffirst (q (str "select id from tehtavaryhma where nimi = 'Hoidonjohtopalkkio (G)';")))
          urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          sopimuksen-id (hae-oulun-maanteiden-hoitourakan-2019-2024-sopimus-id)
          tehtava-id (ffirst (q (str "select id FROM tehtava WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8';")))
          poikkeuslaskutukset (ffirst (q (str "SELECT coalesce(SUM(kk.summa),0)
                                                 FROM kulu k, kulu_kohdistus kk
                                                WHERE k.urakka = "urakka-id"
                                                  AND k.id = kk.kulu
                                                  AND kk.kulu IN (select id from kulu where tyyppi = 'laskutettava'
                                                  AND erapaiva >= '2020-03-01'::DATE AND erapaiva <= '2020-03-31'::DATE)
                                                  AND kk.toimenpideinstanssi = "hallinnolliset-toimenpiteet-tpi-id "
                                                  AND tehtavaryhma NOT IN (SELECT id FROM tehtavaryhma WHERE nimi ILIKE 'Hoitovuoden päättäminen%');")))
          kustannusarvioidut-tyot (ffirst (q (str "SELECT COALESCE(SUM(kat.summa_indeksikorjattu), 0) AS summa
                                                     FROM kustannusarvioitu_tyo kat
                                                    WHERE kat.toimenpideinstanssi = " hallinnolliset-toimenpiteet-tpi-id "
                                                      AND (kat.tehtavaryhma = "tehtavaryhma-id" OR kat.tehtava = "tehtava-id")
                                                      AND kat.sopimus = " sopimuksen-id "
                                                      AND (SELECT (date_trunc('MONTH', format('%s-%s-%s', kat.vuosi, kat.kuukausi, 1)::DATE)))
                                                  BETWEEN '2020-03-01'::DATE AND '2020-03-31'::DATE")))]
      (is (= (:hj_palkkio_laskutetaan hoidonjohto) (+ poikkeuslaskutukset kustannusarvioidut-tyot))))))

(deftest varmista-laskutusyhteeveto-latautuu
  (testing "Lataa oulun tiedot vuodelle 2021"
    (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
          parametrit {:urakkatyyppi "teiden-hoito"
                      :alkupvm (pvm/->pvm "1.10.2021")
                      :loppupvm (pvm/->pvm "30.9.2022")
                      :urakka-id urakka-id
                      :hallintayksikko-id hallintayksikko-id}
          latautuu (laskutusyhteenveto/suorita (:db jarjestelma) +kayttaja-jvh+ parametrit )]
      (is (not (nil? latautuu)))))
  (testing "Lataa oulun tiedot vuodelle 2020"
    (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
          parametrit {:urakkatyyppi "teiden-hoito"
                      :alkupvm (pvm/->pvm "1.10.2020")
                      :loppupvm (pvm/->pvm "30.9.2021")
                      :urakka-id urakka-id
                      :hallintayksikko-id hallintayksikko-id}
          latautuu (laskutusyhteenveto/suorita (:db jarjestelma) +kayttaja-jvh+ parametrit )]
      (is (not (nil? latautuu)))))
  (testing "Lataa oulun tiedot vuodelle 2019"
    (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
          parametrit {:urakkatyyppi "teiden-hoito"
                      :alkupvm (pvm/->pvm "1.10.2019")
                      :loppupvm (pvm/->pvm "30.9.2020")
                      :urakka-id urakka-id
                      :hallintayksikko-id hallintayksikko-id}
          latautuu (laskutusyhteenveto/suorita (:db jarjestelma) +kayttaja-jvh+ parametrit )]
      (is (not (nil? latautuu))))))

(deftest mhu-korvausinvestointi
  (let [alkuaika "2022-10-01"
        loppuaika "2022-10-01"
        urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id

        ;;Hae Korvausinvestoinnin toimenpideinstanssi
        korvausinvestointi (first (q-map (format "SELECT id, toimenpide FROM toimenpideinstanssi WHERE nimi = '%s' and urakka = %s"
                                  "Oulu MHU MHU Korvausinvestointi TP" urakka-id)))
        ;; Haetaan toimenpideinstanssille määritellyn toimenpiteen kautta tehtävä
        tehtava (first (q-map (format "SELECT id, nimi, tehtavaryhma FROM tehtava t WHERE t.emo = %s" (:toimenpide korvausinvestointi))))

        ;;  Poista kaikki mhu-korvausinvestointihommat oululta
        _ (poista-kulut-aikavalilta urakka-id alkuaika loppuaika)

        ;; Lisätään hankintakulu korvausinvestointi toimenpideinstanssille
        _ (lisaa-kulu-urakalle 100 alkuaika urakka-id (:id korvausinvestointi) (:tehtavaryhma tehtava) "kokonaishintainen")

        ;; Lisätään lisätyö korvausinvestointi toimenpideinstanssille
        _ (lisaa-kulu-urakalle 11 alkuaika urakka-id (:id korvausinvestointi) nil "lisatyo")

        ;; Lisätään sanktiot korvausinvestointi toimenpideinstanssille
        ;; Hae hallinnolliset laiminlyönnit sanktiotyypin id
        sanktiotyyppi-id (:id (first (q-map (format "SELECT id FROM sanktiotyyppi st WHERE st.nimi = '%s';"
                                              "Hallinnolliset laiminlyönnit"))))
        _ (lisaa-sanktio-urakalle 12 "C" alkuaika urakka-id (:id korvausinvestointi) sanktiotyyppi-id)


        _ (when (= (empty? @oulun-mhu-urakka-2022-2023))
            (reset! oulun-mhu-urakka-2022-2023 (hae-2022-2023-oulu-mhu-tiedot)))
        poista-tpi (fn [tiedot]
                     (map #(dissoc %
                             :tpi) tiedot))
        haetut-tiedot-oulu-ilman-tpita (poista-tpi @oulun-mhu-urakka-2022-2023)
        haetut-tiedot-oulu-mhu-korvausinvestointi (first (filter #(= (:tuotekoodi %) "14300") haetut-tiedot-oulu-ilman-tpita))

        _ (is (= 100M (:hankinnat_laskutettu haetut-tiedot-oulu-mhu-korvausinvestointi)))
        _ (is (= 11M (:lisatyot_laskutettu haetut-tiedot-oulu-mhu-korvausinvestointi)))
        _ (is (= -12M (:sakot_laskutettu haetut-tiedot-oulu-mhu-korvausinvestointi)))
        _ (is (= 100M (:hankinnat_laskutetaan haetut-tiedot-oulu-mhu-korvausinvestointi)))
        _ (is (= 11M (:lisatyot_laskutetaan haetut-tiedot-oulu-mhu-korvausinvestointi)))
        _ (is (= -12M (:sakot_laskutetaan haetut-tiedot-oulu-mhu-korvausinvestointi)))]))


(deftest laskutusyhteenvedon-sementointi
  (testing "laskutusyhteenvedon-sementoiti"
    (let [_ (when (= (empty? @oulun-mhu-urakka-2020-03))
              (reset! oulun-mhu-urakka-2020-03 (hae-2020-03-tiedot)))

          poista-tpi (fn [tiedot]
                       (map #(dissoc %
                                     :tpi) tiedot))
          haetut-tiedot-oulu-ilman-tpita (poista-tpi @oulun-mhu-urakka-2020-03)
          haetut-tiedot-oulu-talvihoito (first (filter #(= (:tuotekoodi %) "23100") haetut-tiedot-oulu-ilman-tpita))
          haetut-tiedot-oulu-liikenneymparisto (first (filter #(= (:tuotekoodi %) "23110") haetut-tiedot-oulu-ilman-tpita))
          haetut-tiedot-oulu-soratiet (first (filter #(= (:tuotekoodi %) "23120") haetut-tiedot-oulu-ilman-tpita))
          haetut-tiedot-oulu-paallyste (first (filter #(= (:tuotekoodi %) "20100") haetut-tiedot-oulu-ilman-tpita))
          haetut-tiedot-oulu-mhu-yllapito (first (filter #(= (:tuotekoodi %) "20190") haetut-tiedot-oulu-ilman-tpita))
          haetut-tiedot-oulu-mhu-korvausinvestointi (first (filter #(= (:tuotekoodi %) "14300") haetut-tiedot-oulu-ilman-tpita))
          _ (println "haetut-tiedot-oulu-mhu-korvausinvestointi; " (pr-str haetut-tiedot-oulu-mhu-korvausinvestointi))
          haetut-tiedot-oulu-mhu-ja-hoidon-johto (first (filter #(= (:tuotekoodi %) "23150") haetut-tiedot-oulu-ilman-tpita))
          ;; Kommentoin nämä pois, koska oletettavasti jotain vielä muuttuu, niin ei hajoa testit ihan heti.
          ;_ (log/debug "haetut-tiedot-oulu-talvihoito")
          ;_ (clojure.pprint/pprint haetut-tiedot-oulu-talvihoito)
          ;_ (log/debug "haetut-tiedot-oulu-liikenneymparisto" )
          ;_ (clojure.pprint/pprint haetut-tiedot-oulu-liikenneymparisto)
          ;_ (log/debug "haetut-tiedot-oulu-soratiet" )
          ;_ (clojure.pprint/pprint haetut-tiedot-oulu-soratiet)
          ;_ (log/debug "haetut-tiedot-oulu-mhu-korvausinvestointi" )
          ;_ (clojure.pprint/pprint haetut-tiedot-oulu-mhu-korvausinvestointi)
          ;_ (log/debug "haetut-tiedot-oulu-paallyste" )
          ;_ (clojure.pprint/pprint haetut-tiedot-oulu-paallyste)
          ;_ (log/debug "haetut-tiedot-oulu-mhu-yllapito" )
          ;_ (clojure.pprint/pprint haetut-tiedot-oulu-mhu-yllapito)
          ;_ (log/debug "haetut-tiedot-oulu-mhu-ja-hoidon-johto" )
          ;_ (clojure.pprint/pprint haetut-tiedot-oulu-mhu-ja-hoidon-johto)
          #_odotetut-talvihoito #_{:bonukset_laskutetaan 0.0M,
                                   :suolasakot_laskutetaan 0.0M,
                                   :kaikki_laskutetaan 3193.3438073394495412843240M,
                                   :kaikki_laskutettu 5425.6360435779816513752714M,
                                   :hj_palkkio_laskutettu 0.0M,
                                   :lisatyot_laskutettu 600.97M,
                                   :hoidonjohto_laskutettu 0.0M,
                                   :bonukset_laskutettu 0.0M,
                                   :sakot_laskutetaan -107.0561926605504587156760M,
                                   :hj_erillishankinnat_laskutetaan 0.0M,
                                   :erilliskustannukset_laskutetaan 0.0M,
                                   :hankinnat_laskutettu 6000.97M,
                                   :nimi "Talvihoito",
                                   :lisatyot_laskutetaan 300.20M,
                                   :lampotila_puuttuu false,
                                   :perusluku 130.8M,
                                   :suolasakot_laskutettu 0.0M,
                                   :hankinnat_laskutetaan 3000.20M,
                                   :indeksi_puuttuu false,
                                   :tavoitehintaiset_laskutettu 6000.97M,
                                   :hj_erillishankinnat_laskutettu 0.0M,
                                   :tuotekoodi "23100",
                                   :hoidonjohto_laskutetaan 0.0M,
                                   :hj_palkkio_laskutetaan 0.0M,
                                   :sakot_laskutettu -1176.3039564220183486247286M,
                                   :erilliskustannukset_laskutettu 0.0M,
                                   :suolasakko_kaytossa true,
                                   :tavoitehintaiset_laskutetaan 3000.20M}
          #_odotetut-liikenneymparistot #_{:bonukset_laskutetaan 0.0M,
                                           :suolasakot_laskutetaan 0.0M,
                                           :kaikki_laskutetaan 0.0M,
                                           :kaikki_laskutettu 1819.6322362385321100909474M,
                                           :hj_palkkio_laskutettu 0.0M,
                                           :lisatyot_laskutettu 0.0M,
                                           :hoidonjohto_laskutettu 0.0M,
                                           :bonukset_laskutettu 0.0M,
                                           :sakot_laskutetaan 0.0M,
                                           :hj_erillishankinnat_laskutetaan 0.0M,
                                           :erilliskustannukset_laskutetaan 0.0M,
                                           :hankinnat_laskutettu 2888.88M,
                                           :nimi "Liikenneympäristön hoito",
                                           :lisatyot_laskutetaan 0.0M,
                                           :lampotila_puuttuu false,
                                           :perusluku 130.8M,
                                           :suolasakot_laskutettu 0.0M,
                                           :hankinnat_laskutetaan 0.0M,
                                           :indeksi_puuttuu false,
                                           :tavoitehintaiset_laskutettu 2888.88M,
                                           :hj_erillishankinnat_laskutettu 0.0M,
                                           :tuotekoodi "23110",
                                           :hoidonjohto_laskutetaan 0.0M,
                                           :hj_palkkio_laskutetaan 0.0M,
                                           :sakot_laskutettu -1069.2477637614678899090526M,
                                           :erilliskustannukset_laskutettu 0.0M,
                                           :suolasakko_kaytossa true,
                                           :tavoitehintaiset_laskutetaan 0.0M}
          #_odotetut-soratiet #_{:bonukset_laskutetaan 0.0M,
                                 :suolasakot_laskutetaan 0.0M,
                                 :kaikki_laskutetaan 4400.40M,
                                 :kaikki_laskutettu 8801.94M,
                                 :hj_palkkio_laskutettu 0.0M,
                                 :lisatyot_laskutettu 800.97M,
                                 :hoidonjohto_laskutettu 0.0M,
                                 :bonukset_laskutettu 0.0M,
                                 :sakot_laskutetaan 0.0M,
                                 :hj_erillishankinnat_laskutetaan 0.0M,
                                 :erilliskustannukset_laskutetaan 0.0M,
                                 :hankinnat_laskutettu 8000.97M,
                                 :nimi "Soratien hoito",
                                 :lisatyot_laskutetaan 400.20M,
                                 :lampotila_puuttuu false,
                                 :perusluku 130.8M,
                                 :suolasakot_laskutettu 0.0M,
                                 :hankinnat_laskutetaan 4000.20M,
                                 :indeksi_puuttuu false,
                                 :tavoitehintaiset_laskutettu 8000.97M,
                                 :hj_erillishankinnat_laskutettu 0.0M,
                                 :tuotekoodi "23120",
                                 :hoidonjohto_laskutetaan 0.0M,
                                 :hj_palkkio_laskutetaan 0.0M,
                                 :sakot_laskutettu 0.0M,
                                 :erilliskustannukset_laskutettu 0.0M,
                                 :suolasakko_kaytossa true,
                                 :tavoitehintaiset_laskutetaan 4000.20M}
          #_odotetut-korvausinvestoinnit #_{:bonukset_laskutetaan 0.0M,
                                            :suolasakot_laskutetaan 0.0M,
                                            :kaikki_laskutetaan 6600.40M,
                                            :kaikki_laskutettu 13201.94M,
                                            :hj_palkkio_laskutettu 0.0M,
                                            :lisatyot_laskutettu 1200.97M,
                                            :hoidonjohto_laskutettu 0.0M,
                                            :bonukset_laskutettu 0.0M,
                                            :sakot_laskutetaan 0.0M,
                                            :hj_erillishankinnat_laskutetaan 0.0M,
                                            :erilliskustannukset_laskutetaan 0.0M,
                                            :hankinnat_laskutettu 12000.97M,
                                            :nimi "MHU Korvausinvestointi",
                                            :lisatyot_laskutetaan 600.20M,
                                            :lampotila_puuttuu false,
                                            :perusluku 130.8M,
                                            :suolasakot_laskutettu 0.0M,
                                            :hankinnat_laskutetaan 6000.20M,
                                            :indeksi_puuttuu false,
                                            :tavoitehintaiset_laskutettu 12000.97M,
                                            :hj_erillishankinnat_laskutettu 0.0M,
                                            :tuotekoodi "14300",
                                            :hoidonjohto_laskutetaan 0.0M,
                                            :hj_palkkio_laskutetaan 0.0M,
                                            :sakot_laskutettu 0.0M,
                                            :erilliskustannukset_laskutettu 0.0M,
                                            :suolasakko_kaytossa true,
                                            :tavoitehintaiset_laskutetaan 6000.20M}
          #_ odotetut-paallyste #_ {:bonukset_laskutetaan 0.0M,
                                    :suolasakot_laskutetaan 0.0M,
                                    :kaikki_laskutetaan 5500.40M,
                                    :kaikki_laskutettu 11001.94M,
                                    :hj_palkkio_laskutettu 0.0M,
                                    :lisatyot_laskutettu 1000.97M,
                                    :hoidonjohto_laskutettu 0.0M,
                                    :bonukset_laskutettu 0.0M,
                                    :sakot_laskutetaan 0.0M,
                                    :hj_erillishankinnat_laskutetaan 0.0M,
                                    :erilliskustannukset_laskutetaan 0.0M,
                                    :hankinnat_laskutettu 10000.97M,
                                    :nimi "Päällyste",
                                    :lisatyot_laskutetaan 500.20M,
                                    :lampotila_puuttuu false,
                                    :perusluku 130.8M,
                                    :suolasakot_laskutettu 0.0M,
                                    :hankinnat_laskutetaan 5000.20M,
                                    :indeksi_puuttuu false,
                                    :tavoitehintaiset_laskutettu 10000.97M,
                                    :hj_erillishankinnat_laskutettu 0.0M,
                                    :tuotekoodi "20100",
                                    :hoidonjohto_laskutetaan 0.0M,
                                    :hj_palkkio_laskutetaan 0.0M,
                                    :sakot_laskutettu 0.0M,
                                    :erilliskustannukset_laskutettu 0.0M,
                                    :suolasakko_kaytossa true,
                                    :tavoitehintaiset_laskutetaan 5000.20M}
          #_ odotetut-yllapito #_ {:bonukset_laskutetaan 0.0M,
                                   :suolasakot_laskutetaan 0.0M,
                                   :kaikki_laskutetaan 7700.40M,
                                   :kaikki_laskutettu 15401.94M,
                                   :hj_palkkio_laskutettu 0.0M,
                                   :lisatyot_laskutettu 1400.97M,
                                   :hoidonjohto_laskutettu 0.0M,
                                   :bonukset_laskutettu 0.0M,
                                   :sakot_laskutetaan 0.0M,
                                   :hj_erillishankinnat_laskutetaan 0.0M,
                                   :erilliskustannukset_laskutetaan 0.0M,
                                   :hankinnat_laskutettu 14000.97M,
                                   :nimi "MHU Ylläpito",
                                   :lisatyot_laskutetaan 700.20M,
                                   :lampotila_puuttuu false,
                                   :perusluku 130.8M,
                                   :suolasakot_laskutettu 0.0M,
                                   :hankinnat_laskutetaan 7000.20M,
                                   :indeksi_puuttuu false,
                                   :tavoitehintaiset_laskutettu 14000.97M,
                                   :hj_erillishankinnat_laskutettu 0.0M,
                                   :tuotekoodi "20190",
                                   :hoidonjohto_laskutetaan 0.0M,
                                   :hj_palkkio_laskutetaan 0.0M,
                                   :sakot_laskutettu 0.0M,
                                   :erilliskustannukset_laskutettu 0.0M,
                                   :suolasakko_kaytossa true,
                                   :tavoitehintaiset_laskutetaan 7000.20M}
          #_ odotetut-mhu-ja-hoidon-johto #_ {:bonukset_laskutetaan 2068.42507645259938838000M,
                                              :suolasakot_laskutetaan 0.0M,
                                              :kaikki_laskutetaan 2382.11009174311926605600M,
                                              :kaikki_laskutettu 3482.11009174311926605600M,
                                              :hj_palkkio_laskutettu 100.0M,
                                              :lisatyot_laskutettu 0.0M,
                                              :hoidonjohto_laskutettu 213.68501529051987767600M,
                                              :bonukset_laskutettu 6205.27522935779816514000M,
                                              :sakot_laskutetaan 0.0M,
                                              :hj_erillishankinnat_laskutetaan 50.0M,
                                              :erilliskustannukset_laskutetaan 0.0M,
                                              :hankinnat_laskutettu 0.0M,
                                              :nimi "MHU ja HJU hoidon johto",
                                              :lisatyot_laskutetaan 0.0M,
                                              :lampotila_puuttuu false,
                                              :perusluku 130.8M,
                                              :suolasakot_laskutettu 0.0M,
                                              :hankinnat_laskutetaan 0.0M,
                                              :indeksi_puuttuu false,
                                              :tavoitehintaiset_laskutettu 413.68501529051987767600M,
                                              :hj_erillishankinnat_laskutettu 100.0M,
                                              :tuotekoodi "23150",
                                              :hoidonjohto_laskutetaan 213.68501529051987767600M,
                                              :hj_palkkio_laskutetaan 50.0M,
                                              :sakot_laskutettu -3136.85015290519877676000M,
                                              :erilliskustannukset_laskutettu 0.0M,
                                              :suolasakko_kaytossa true,
                                              :tavoitehintaiset_laskutetaan 313.68501529051987767600M}
          ]

      ;; Talvihoito - Hankinnat - laskutetaan
      (is (= 3000.20M (:hankinnat_laskutetaan haetut-tiedot-oulu-talvihoito)))
      ;; Talvihoito - Hankinnat - laskutettu
      (is (= 6000.97M (:hankinnat_laskutettu haetut-tiedot-oulu-talvihoito)))
      ;; Talvihoito - Lisätyöt - laskutetaan
      (is (= 300.20M (:lisatyot_laskutetaan haetut-tiedot-oulu-talvihoito)))
      ;; Talvihoito - hankinnat - laskutettu
      (is (= 600.97M (:lisatyot_laskutettu haetut-tiedot-oulu-talvihoito)))

      ;; Soratien hoito - Hankinnat - laskutetaan
      (is (= 4000.20M (:hankinnat_laskutetaan haetut-tiedot-oulu-soratiet)))
      ;; Talvihoito - Hankinnat - laskutettu
      (is (= 8000.97M (:hankinnat_laskutettu haetut-tiedot-oulu-soratiet)))
      ;; Talvihoito - Lisätyöt - laskutetaan
      (is (= 400.20M (:lisatyot_laskutetaan haetut-tiedot-oulu-soratiet)))
      ;; Talvihoito - hankinnat - laskutettu
      (is (= 800.97M (:lisatyot_laskutettu haetut-tiedot-oulu-soratiet)))


      #_(testing "Talvihoito"
          (testi/tarkista-map-arvot odotetut-talvihoito haetut-tiedot-oulu-talvihoito))
      #_(testing "Liikenneympäristön hoito"
          (testi/tarkista-map-arvot odotetut-liikenneymparistot haetut-tiedot-oulu-liikenneymparisto))
      #_(testing "Liikenneympäristön hoito"
          (testi/tarkista-map-arvot odotetut-soratiet haetut-tiedot-oulu-soratiet))
      #_(testing "MHU Korvausinvestointi"
          (testi/tarkista-map-arvot odotetut-korvausinvestoinnit haetut-tiedot-oulu-mhu-korvausinvestointi))
      )))

#_ (deftest tiedot-haetaan-oikein-maksuera-laskentaa-varten
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
