(ns harja.palvelin.palvelut.kustannusten-seuranta-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.set :as set]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :db-replica (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :kustannusten-seuranta (component/using
                                                 (kustannusten-seuranta/->KustannustenSeuranta)
                                                 [:http-palvelin :db :db-replica])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn- hae-kustannukset [urakka-id alkupvm loppupvm]
  (kutsu-palvelua
    (:http-palvelin jarjestelma)
    :urakan-kustannusten-seuranta-paaryhmittain
    +kayttaja-tero+
    {:urakka-id urakka-id
     :alkupvm alkupvm
     :loppupvm loppupvm}))

(defn- erilliskustannukset-budjetoitu-sql-haku [urakka alkupvm loppupvm]
  (str "WITH urakan_toimenpideinstanssi_23150 AS
         (SELECT tpi.id AS id
          FROM toimenpideinstanssi tpi
                   JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                   JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
               maksuera m
          WHERE tpi.urakka = " urakka "
            AND m.toimenpideinstanssi = tpi.id
            AND tpk2.koodi = '23150')
        SELECT kt.summa as summa
        FROM kustannusarvioitu_tyo kt
        WHERE kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
          AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE)
          AND kt.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Erillishankinnat (W)');"))

(defn erilliskustannusten-toteumat-sql-haku [urakka alkupvm loppupvm]
  (str "SELECT
          lk.summa AS toteutunut_summa,
          0 AS budjetoitu_summa
        FROM lasku_kohdistus lk
                 LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = lk.tehtava
                 JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma,
             toimenpideinstanssi tpi,
             toimenpidekoodi tk,
             lasku l
        WHERE l.urakka = " urakka "
          AND l.erapaiva BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE
          AND lk.lasku = l.id
          AND lk.toimenpideinstanssi = tpi.id
          AND tpi.toimenpide = tk.id
          AND tr.nimi = 'Erillishankinnat (W)'
          AND (tk.koodi = '23151' OR tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')"))

(defn- hoidonjohdonpalkkio-budjetoidut-sql-haku [urakka alkupvm loppupvm]
  (str "WITH urakan_toimenpideinstanssi_23150 AS
         (SELECT tpi.id AS id
          FROM toimenpideinstanssi tpi
                   JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                   JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
               maksuera m
          WHERE tpi.urakka = " urakka "
            AND m.toimenpideinstanssi = tpi.id
            AND tpk2.koodi = '23150')
        SELECT kt.summa as summa
        FROM kustannusarvioitu_tyo kt
        WHERE kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
          AND (kt.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoidonjohtopalkkio (G)')
               OR kt.tehtava = (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744')
               )
          AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE);"))

(defn- hoidonjohdonpalkkio-toteumat-sql-haku [urakka alkupvm loppupvm]
  (str "SELECT
          lk.summa AS toteutunut_summa,
          0 AS budjetoitu_summa
        FROM lasku_kohdistus lk
                 LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = lk.tehtava
                 JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma,
             toimenpideinstanssi tpi,
             toimenpidekoodi tk,
             lasku l
        WHERE l.urakka = " urakka "
          AND l.erapaiva BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE
          AND lk.lasku = l.id
          AND lk.toimenpideinstanssi = tpi.id
          AND tpi.toimenpide = tk.id
          AND tr.nimi = 'Hoidonjohtopalkkio (G)'
          AND (tk.koodi = '23151' OR tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')"))

(defn- johto-ja-hallintokorvaukset-budjetoidut-sql-haku [urakka alkupvm loppupvm]
  (str "WITH urakan_toimenpideinstanssi_23150 AS
         (SELECT tpi.id AS id
          FROM toimenpideinstanssi tpi
                   JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                   JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
               maksuera m
          WHERE tpi.urakka = " urakka "
            AND m.toimenpideinstanssi = tpi.id
            AND tpk2.koodi = '23150')
        SELECT (hjh.tunnit * hjh.tuntipalkka) as summa
        FROM johto_ja_hallintokorvaus hjh
         LEFT JOIN johto_ja_hallintokorvaus_toimenkuva jjht on hjh.\"toimenkuva-id\" = jjht.id
        WHERE hjh.\"urakka-id\" = " urakka "
        AND (concat(hjh.vuosi, '-', hjh.kuukausi, '-01')::DATE BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE)
        UNION ALL
        SELECT kt.summa as summa
        FROM kustannusarvioitu_tyo kt
        WHERE kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
          AND (kt.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Johto- ja hallintokorvaus (J)')
               OR kt.tehtava = (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')
               )
          AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE);"))

(defn- johto-ja-hallintokorvaukset-toteutuneet-sql-haku [urakka alkupvm loppupvm]
  (str "SELECT
          lk.summa AS toteutunut_summa,
          0 AS budjetoitu_summa
        FROM lasku_kohdistus lk
                 LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = lk.tehtava
                 JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma,
             toimenpideinstanssi tpi,
             toimenpidekoodi tk,
             lasku l
        WHERE l.urakka = " urakka "
          AND l.erapaiva BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE
          AND lk.lasku = l.id
          AND lk.toimenpideinstanssi = tpi.id
          AND tpi.toimenpide = tk.id
          AND (tr.nimi = 'Johto- ja hallintokorvaus (J)' OR tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')
          AND (tk.koodi = '23151' OR tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')"))

(defn- hankintakustannukset-budjetoitu-sql-haku [urakka alkupvm loppupvm]
  (str "
  SELECT
     kt.summa,
     0 AS toteutunut_summa,
     CASE
      WHEN tk.koodi = '23104' THEN 'Talvihoito'
      WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
      WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
      WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
      WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
      WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
      WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
       END AS toimenpide
  FROM toimenpidekoodi tk,
      kustannusarvioitu_tyo kt,
      toimenpideinstanssi tpi,
      sopimus s
  WHERE s.urakka = " urakka "
   AND kt.sopimus = s.id
   AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE)
   AND kt.toimenpideinstanssi = tpi.id
   AND tpi.toimenpide = tk.id
   AND (tk.koodi = '23104' -- talvihoito
     OR tk.koodi = '23116' -- liikenneympariston-hoito
     OR tk.koodi = '23124' -- sorateiden-hoito
     OR tk.koodi = '20107' -- paallystepaikkaukset
     OR tk.koodi = '20191' -- mhu-yllapito
     OR tk.koodi = '14301' -- mhu-korvausinvestointi
     OR tk.koodi = '23151' -- mhu-johto
     )
UNION ALL
  SELECT
    kt.summa,
    0 AS toteutunut_summa,
    CASE
    WHEN tk.koodi = '23104' THEN 'Talvihoito'
    WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
    WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
    WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
    WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
    WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
    WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
    END AS toimenpide
  FROM toimenpidekoodi tk,
    kiinteahintainen_tyo kt,
    toimenpideinstanssi tpi
  WHERE tpi.urakka = " urakka "
    AND kt.toimenpideinstanssi = tpi.id
    AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE)
    AND tpi.toimenpide = tk.id
    AND (tk.koodi = '23104' -- talvihoito
     OR tk.koodi = '23116' -- liikenneympariston-hoito
     OR tk.koodi = '23124' -- sorateiden-hoito
     OR tk.koodi = '20107' -- paallystepaikkaukset
     OR tk.koodi = '20191' -- mhu-yllapito
      OR tk.koodi = '14301' -- mhu-korvausinvestointi
      OR tk.koodi = '23151' -- mhu-johto
      )"))

(defn- hankintakustannukset-toteutuneet-sql-haku [urakka alkupvm loppupvm]
  (str "SELECT lk.summa AS toteutunut_summa,
       0 AS budjetoitu_summa,
       CASE
           WHEN lk.maksueratyyppi::TEXT = 'kokonaishintainen' THEN 'hankinta'
           ELSE 'rahavaraus'
           END                 AS toimenpideryhma,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           --WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
           END                 AS toimenpide
FROM lasku_kohdistus lk
         LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = lk.tehtava
         JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma,
     toimenpideinstanssi tpi,
     toimenpidekoodi tk,
     lasku l
WHERE l.urakka = " urakka "
  AND l.erapaiva BETWEEN '" alkupvm "' ::DATE AND '" loppupvm "'::DATE
  AND lk.lasku = l.id
  AND lk.toimenpideinstanssi = tpi.id
  AND tpi.toimenpide = tk.id
  -- Näillä toimenpidekoodi.koodi rajauksilla rajataan johto- ja hallintakorvaus, hoidonjohdonpalkkio ja erilliskorvaus ulos
  AND (tk.koodi = '23104' OR tk.koodi = '23116'
    OR tk.koodi = '23124' OR tk.koodi = '20107' OR tk.koodi = '20191' OR
       tk.koodi = '14301')"))

;; Kustannusten seuranta koostuu budjetoiduista kustannuksista ja niihin liitetyistä toteutuneista (laskutetuista) kustannuksista.
;; Seuranta jaetaan monella eri kriteerillä osiin, jotta seuranta helpottuu
;; (mm. Hankintakustannukset, Johto- ja Hallintakorvaus, Hoidonjohdonpalkkio, Erillishankinnat)

;; Testataan/vertaillaan ensimmäisenä erillishankintojen budjetoituja summia
(deftest budjetoidut-erillishankinnat-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        vastaus (hae-kustannukset urakka-id alkupvm loppupvm)
        erillishankinnat (filter
                           #(when (= "erillishankinnat" (:paaryhma %))
                              true)
                           vastaus)
        eh-summa (apply + (map #(:budjetoitu_summa %) erillishankinnat))
        erillishankinnat-sql (q (erilliskustannukset-budjetoitu-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) erillishankinnat-sql))
        _ (println "erillishankinnat-sql" eh-summa (pr-str erillishankinnat-sql))
        _ (println "erillishankinnat" sql-summa (pr-str erillishankinnat))]
    (is (= eh-summa sql-summa))))

;; Testataan/vertaillaan erillishankintojen toteutumia
(deftest toteutuneet-erillishankinnat-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        vastaus (hae-kustannukset urakka-id alkupvm loppupvm)
        erillishankinnat (filter
                           #(when (= "erillishankinnat" (:paaryhma %))
                              true)
                           vastaus)
        eh-summa (apply + (map #(:toteutunut_summa %) erillishankinnat))
        erillishankinnat-sql (q (erilliskustannusten-toteumat-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) erillishankinnat-sql))
        _ (println "erillishankinnat-sql" eh-summa (pr-str erillishankinnat-sql))
        _ (println "erillishankinnat" sql-summa (pr-str erillishankinnat))]
    (is (= eh-summa sql-summa))))

;; Testataan/vertaillaan Hoidonjohdonpalkkioiden budjetoituja summia
(deftest budjetoidut-hoidonjohdonpalkkiot-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        vastaus (hae-kustannukset urakka-id alkupvm loppupvm)
        hj_palkkiot (filter
                      #(when (= "hoidonjohdonpalkkio" (:paaryhma %))
                         true)
                      vastaus)
        hj-summa (apply + (map #(:budjetoitu_summa %) hj_palkkiot))
        hj-sql (q (hoidonjohdonpalkkio-budjetoidut-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) hj-sql))
        _ (println "hj-sql" sql-summa (pr-str hj-sql))
        _ (println "hj" hj-summa (pr-str hj_palkkiot))]
    (is (= hj-summa sql-summa))))

;; Testataan/vertaillaan Hoidonjohdonpalkkioiden toteutuneita summia
(deftest toteutuneet-hoidonjohdonpalkkiot-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        vastaus (hae-kustannukset urakka-id alkupvm loppupvm)
        hj_palkkiot (filter
                      #(when (= "hoidonjohdonpalkkio" (:paaryhma %))
                         true)
                      vastaus)
        hj-summa (apply + (map #(:toteutunut_summa %) hj_palkkiot))
        hj-sql (q (hoidonjohdonpalkkio-toteumat-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) hj-sql))
        _ (println "hj-sql" sql-summa (pr-str hj-sql))
        _ (println "hj" hj-summa (pr-str hj_palkkiot))]
    (is (= hj-summa sql-summa))))

;; Testataan/vertaillaan Johto- ja hallintokorvauksen budjetoituja summia
;; Johto- ja hallintokorvaukset kuuluu palkat ja muut kulut, kuten toimistokulut yms.
(deftest budjetoidut-johtoja-hallintokorvaukset-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        vastaus (hae-kustannukset urakka-id alkupvm loppupvm)
        johto_ja_h_korvaukset (filter
                                #(when (= "johto-ja-hallintakorvaus" (:paaryhma %))
                                   true)
                                vastaus)
        jjh-summa (apply + (map #(:budjetoitu_summa %) johto_ja_h_korvaukset))
        jjh-sql (q (johto-ja-hallintokorvaukset-budjetoidut-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) jjh-sql))
        _ (println "jjh-sql" sql-summa (pr-str jjh-sql))
        _ (println "johto_ja_h_korvaukset" jjh-summa (pr-str johto_ja_h_korvaukset))]
    (is (= jjh-summa sql-summa))))

;; Testataan/vertaillaan Johto- ja hallintokorvauksen toteutuneita summia
(deftest toteutuneet-johtoja-hallintokorvaukset-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        vastaus (hae-kustannukset urakka-id alkupvm loppupvm)
        johto_ja_h_korvaukset (filter
                                #(when (= "johto-ja-hallintakorvaus" (:paaryhma %))
                                   true)
                                vastaus)
        jjh-summa (apply + (map #(:toteutunut_summa %) johto_ja_h_korvaukset))
        jjh-sql (q (johto-ja-hallintokorvaukset-toteutuneet-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) jjh-sql))
        _ (println "jjh-sql" sql-summa (pr-str jjh-sql))
        _ (println "johto_ja_h_korvaukset" jjh-summa (pr-str johto_ja_h_korvaukset))]
    (is (= jjh-summa sql-summa))))

;; Testataan/vertaillaan Hankintakustannusten budjetoituja summia
;; Hankintakustannusten alle tulee toimenpideryhmät kuten talvihoito, liikenneympäristöhoito, yms.
(deftest budjetoidut-hankintakustannukset-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        vastaus (hae-kustannukset urakka-id alkupvm loppupvm)
        hankintakustannukset (filter
                               #(when (= "hankintakustannukset" (:paaryhma %))
                                  true)
                               vastaus)
        h-summa (apply + (map #(:budjetoitu_summa %) hankintakustannukset))
        h-sql (q (hankintakustannukset-budjetoitu-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) h-sql))
        _ (println "h-sql" sql-summa (pr-str h-sql))
        _ (println "hankintakustannukset" h-summa (pr-str hankintakustannukset))]
    (is (= h-summa sql-summa))))

;; Testataan/vertaillaan Hankintakustannusten toteutuneita summia
;; Hankintakustannusten alle tulee toimenpideryhmät kuten talvihoito, liikenneympäristöhoito, yms.
(deftest toteutuneet-hankintakustannukset-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        vastaus (hae-kustannukset urakka-id alkupvm loppupvm)
        hankintakustannukset (filter
                               #(when (= "hankintakustannukset" (:paaryhma %))
                                  true)
                               vastaus)
        h-summa (apply + (map #(:toteutunut_summa %) hankintakustannukset))
        h-sql (q (hankintakustannukset-toteutuneet-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) h-sql))
        _ (println "h-sql" sql-summa (pr-str h-sql))
        _ (println "hankintakustannukset" h-summa (pr-str hankintakustannukset))]
    (is (= h-summa sql-summa))))

;; TODO: Tee erillinen urakka, jolle syötetään suunnitelmat ja kulut ja tarkista, että kaikki löytyy tietokannasta oikein