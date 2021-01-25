(ns harja.palvelin.palvelut.kustannusten-seuranta-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.palvelut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.palvelin.palvelut.laskut :as laskut]
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
                        ;:excel-vienti
                        #_(component/using
                            (excel-vienti/luo-excel-vienti)
                            [:http-palvelin])
                        :kustannusten-seuranta (component/using
                                                 (kustannusten-seuranta/->KustannustenSeuranta)
                                                 [:http-palvelin :db :db-replica #_:excel-vienti])
                        :laskut (component/using
                                  (laskut/->Laskut)
                                  [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn- hae-kustannukset [urakka-id hoitokauden-alkuvuosi alkupvm loppupvm]
  (kutsu-palvelua
    (:http-palvelin jarjestelma)
    :urakan-kustannusten-seuranta-paaryhmittain
    +kayttaja-tero+
    {:urakka-id urakka-id
     :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
     :alkupvm alkupvm
     :loppupvm loppupvm}))

(defn- lataa-excel [urakka-id hoitokauden-alkuvuosi alkupvm loppupvm]
  (kutsu-palvelua
    (:http-palvelin jarjestelma)
    :kustannukset
    +kayttaja-tero+
    {:urakka-id urakka-id
     :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
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

(defn erilliskustannusten-toteumat-sql-haku [urakka alkupvm loppupvm hoitokauden-alkuvuosi]
  (let [haku-str (str "SELECT
          lk.summa AS toteutunut_summa,
          0 AS budjetoitu_summa
        FROM lasku_kohdistus lk
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
          AND (tk.koodi = '23151' OR tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')
        UNION ALL
        SELECT
       (SELECT korotettuna
        FROM laske_kuukauden_indeksikorotus(" hoitokauden-alkuvuosi ", 9,
                                            (SELECT u.indeksi as nimi FROM urakka u WHERE u.id = " urakka "),
                                            coalesce(t.summa, 0),
                                            (SELECT indeksilaskennan_perusluku(" urakka "::INTEGER))))
                                               AS toteutunut_summa,
        0 AS budjetoitu_summa
        FROM toteutuneet_kustannukset t
              JOIN tehtavaryhma tr ON tr.id = t.tehtavaryhma AND tr.nimi = 'Erillishankinnat (W)',
             toimenpideinstanssi tpi,
             toimenpidekoodi tk
        WHERE t.urakka_id = " urakka "
          AND (concat(t.vuosi, '-', t.kuukausi, '-01')::DATE BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE)
          AND t.toimenpideinstanssi = tpi.id
          AND tpi.toimenpide = tk.id
          AND (tk.koodi = '23151' OR tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')")]
    haku-str))

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

(defn- hoidonjohdonpalkkio-toteumat-sql-haku [urakka alkupvm loppupvm hoitokauden-alkuvuosi]
  (let [haku-str (str "SELECT
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
          AND (tk.koodi = '23151' OR tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')
        UNION ALL
        SELECT
          (SELECT korotettuna
             FROM laske_kuukauden_indeksikorotus(" hoitokauden-alkuvuosi ", 9,
                                            (SELECT u.indeksi as nimi FROM urakka u WHERE u.id = " urakka "),
                                            coalesce(t.summa, 0),
                                            (SELECT indeksilaskennan_perusluku(" urakka "::INTEGER))))
                                               AS toteutunut_summa,
        0 AS budjetoitu_summa
        FROM toteutuneet_kustannukset t
              LEFT JOIN tehtavaryhma tr ON tr.id = t.tehtavaryhma
              LEFT JOIN toimenpidekoodi tk_tehtava ON t.tehtava = tk_tehtava.id,
             toimenpideinstanssi tpi,
             toimenpidekoodi tk
        WHERE t.urakka_id = " urakka "
          AND (concat(t.vuosi, '-', t.kuukausi, '-01')::DATE BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE)
          AND t.toimenpideinstanssi = tpi.id
          AND tpi.toimenpide = tk.id
          AND (tr.nimi = 'Hoidonjohtopalkkio (G)' OR tk_tehtava.yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744')
          AND (tk.koodi = '23151' OR tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388');")]
    haku-str))

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
         WHERE hjh.\"urakka-id\" = " urakka "
           AND (concat(hjh.vuosi, '-', hjh.kuukausi, '-01')::DATE BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE)

        UNION ALL

        SELECT kt.summa as summa
          FROM kustannusarvioitu_tyo kt, sopimus s
          WHERE s.urakka = " urakka "
          AND kt.sopimus = s.id
          AND kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
            AND kt.tehtava = (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')
          AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE);"))

(defn- johto-ja-hallintokorvaukset-toteutuneet-sql-haku [urakka alkupvm loppupvm hoitokauden-alkuvuosi]
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
          AND (tk.koodi = '23151' OR tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')
        UNION ALL
        SELECT
       (SELECT korotettuna
        FROM laske_kuukauden_indeksikorotus(" hoitokauden-alkuvuosi ", 9,
                                            (SELECT u.indeksi as nimi FROM urakka u WHERE u.id = " urakka "),
                                            coalesce(t.summa, 0),
                                            (SELECT indeksilaskennan_perusluku(" urakka "::INTEGER))))
                                               AS toteutunut_summa,
        0 AS budjetoitu_summa
        FROM toteutuneet_kustannukset t
              LEFT JOIN tehtavaryhma tr ON tr.id = t.tehtavaryhma
              LEFT JOIN toimenpidekoodi tk_tehtava ON t.tehtava = tk_tehtava.id,
             toimenpideinstanssi tpi,
             toimenpidekoodi tk
        WHERE t.urakka_id = " urakka "
          AND (concat(t.vuosi, '-', t.kuukausi, '-01')::DATE BETWEEN '" alkupvm "'::DATE AND '" loppupvm "'::DATE)
          AND t.toimenpideinstanssi = tpi.id
          AND tpi.toimenpide = tk.id
          AND (tr.nimi = 'Johto- ja hallintokorvaus (J)' OR tk_tehtava.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')
          AND (tk.koodi = '23151' OR tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388');"))

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
   AND kt.tyyppi::TEXT != 'akillinen-hoitotyo'
   AND kt.tyyppi::TEXT != 'vahinkojen-korjaukset'
   AND (tk.koodi = '23104' -- talvihoito
     OR tk.koodi = '23116' -- liikenneympariston-hoito
     OR tk.koodi = '23124' -- sorateiden-hoito
     OR tk.koodi = '20107' -- paallystepaikkaukset
     OR tk.koodi = '20191' -- mhu-yllapito
     OR tk.koodi = '14301' -- mhu-korvausinvestointi
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
      )"))

(defn- hankintakustannukset-toteutuneet-sql-haku [urakka alkupvm loppupvm]
  (let [haku-str (str "SELECT lk.summa AS toteutunut_summa,
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
       END                 AS toimenpide
       FROM lasku_kohdistus lk
             LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = lk.tehtava
             LEFT JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma,
            toimenpideinstanssi tpi,
           toimenpidekoodi tk,
           lasku l
       WHERE l.urakka = " urakka "
         AND l.erapaiva BETWEEN '" alkupvm "' ::DATE AND '" loppupvm "'::DATE
         AND lk.lasku = l.id
         AND l.poistettu IS NOT TRUE
         AND lk.toimenpideinstanssi = tpi.id
         AND lk.poistettu IS NOT TRUE
         AND tpi.toimenpide = tk.id
         AND lk.maksueratyyppi::TEXT = 'kokonaishintainen'
         AND tr.nimi != 'Tilaajan rahavaraus (T3)'
  -- Näillä toimenpidekoodi.koodi rajauksilla rajataan johto- ja hallintakorvaus, hoidonjohdonpalkkio ja erilliskorvaus ulos
  AND (tk.koodi = '23104' OR tk.koodi = '23116'
    OR tk.koodi = '23124' OR tk.koodi = '20107' OR tk.koodi = '20191' OR
       tk.koodi = '14301')")]
    haku-str))

(defn- lisatyot-sql-haku [urakka alkupvm loppupvm]
  (let [haku-str (str "SELECT lk.summa AS toteutunut_summa,
             0 AS budjetoitu_summa,
             CASE
                 WHEN tk.koodi = '23104' THEN 'Talvihoito'
                 WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
                 WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
                 WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
                 WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
                 WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
                 WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
             END                 AS toimenpide
        FROM lasku_kohdistus lk
                 LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = lk.tehtava
                 LEFT JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma,
             toimenpideinstanssi tpi,
             toimenpidekoodi tk,
             lasku l
        WHERE l.urakka = " urakka "
          AND l.erapaiva BETWEEN '" alkupvm "' ::DATE AND '" loppupvm "'::DATE
          AND l.poistettu IS NOT TRUE
          AND lk.maksueratyyppi = 'lisatyo'
          AND lk.lasku = l.id
          AND lk.poistettu IS NOT TRUE
          AND lk.toimenpideinstanssi = tpi.id
          AND tpi.toimenpide = tk.id")]
    haku-str)
  )

(defn- bonukset-toteutuneet-sql-haku [urakka alkupvm loppupvm hoitokauden-alkuvuosi]
  (str " SELECT CASE
                WHEN ek.tyyppi::TEXT = 'lupausbonus' OR ek.tyyppi::TEXT = 'asiakastyytyvaisyysbonus'
                THEN SUM((SELECT korotettuna
                                 FROM laske_kuukauden_indeksikorotus("hoitokauden-alkuvuosi"::INTEGER, 9::INTEGER,
                                                                      (SELECT u.indeksi as nimi FROM urakka u WHERE u.id = "urakka")::VARCHAR,
                                                                      coalesce(ek.rahasumma, 0)::NUMERIC,
                                                                      (SELECT indeksilaskennan_perusluku("urakka"::INTEGER))::NUMERIC)))
                ELSE SUM(ek.rahasumma)
                END        AS toteutunut_summa,
                0          AS budjetoitu_summa,
                'bonus'    AS toimenpideryhma,
                'bonukset' AS paaryhma
           FROM erilliskustannus ek,
                  sopimus s
          WHERE s.urakka = 35
            AND ek.sopimus = s.id
            AND ek.toimenpideinstanssi = (SELECT tpi.id AS id
                                            FROM toimenpideinstanssi tpi
                                                 JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                                                 JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
                                                 maksuera m
                                           WHERE tpi.urakka = "urakka"
                                             AND m.toimenpideinstanssi = tpi.id
                                             AND tpk2.koodi = '23150')
            AND ek.pvm BETWEEN '"alkupvm"'::DATE AND '"loppupvm"'::DATE
            AND ek.poistettu IS NOT TRUE
          GROUP BY ek.tyyppi"))

(defn- bonukset-budjetoitu-sql-haku [urakka alkupvm loppupvm hoitokauden-alkuvuosi]
  (str "SELECT kt.summa                                  AS budjetoitu_summa,
         0                                              AS toteutunut_summa,
         'bonus'                                        AS toimenpideryhma,
         'bonukset'                                     AS paaryhma
         FROM kustannusarvioitu_tyo kt,
         sopimus s
         WHERE s.urakka = "urakka"
         AND kt.toimenpideinstanssi = (SELECT tpi.id AS id
                                         FROM toimenpideinstanssi tpi
                                              JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                                              JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
                                              maksuera m
                                        WHERE tpi.urakka = "urakka"
                                          AND m.toimenpideinstanssi = tpi.id
                                           AND tpk2.koodi = '23150')
         AND kt.tehtava IS NULL
         AND kt.tehtavaryhma = (select id from tehtavaryhma tr where tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54')
         AND kt.sopimus = s.id
         AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN '"alkupvm"'::DATE AND '"loppupvm"'::DATE)")
  )

(deftest hae-olemattomia-kustannuksia
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)]
    (is (thrown? Exception (hae-kustannukset urakka-id nil nil nil)))))


;; Kustannusten seuranta koostuu budjetoiduista kustannuksista ja niihin liitetyistä toteutuneista (laskutetuista) kustannuksista.
;; Seuranta jaetaan monella eri kriteerillä osiin, jotta seuranta helpottuu
;; (mm. Hankintakustannukset, Johto- ja Hallintakorvaus, Hoidonjohdonpalkkio, Erillishankinnat)

;; Testataan/vertaillaan ensimmäisenä erillishankintojen budjetoituja summia
(deftest budjetoidut-erillishankinnat-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        hoitokauden-alkuvuosi 2019
        vastaus (hae-kustannukset urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)
        erillishankinnat (filter
                           #(when (= "erillishankinnat" (:paaryhma %))
                              true)
                           vastaus)
        eh-summa (apply + (map #(:budjetoitu_summa %) erillishankinnat))
        erillishankinnat-sql (q (erilliskustannukset-budjetoitu-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) erillishankinnat-sql))]
    (is (= eh-summa sql-summa))))

;; Testataan/vertaillaan erillishankintojen toteutumia
(deftest toteutuneet-erillishankinnat-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        hoitokauden-alkuvuosi 2019
        vastaus (hae-kustannukset urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)
        erillishankinnat (filter
                           #(when (= "erillishankinnat" (:paaryhma %))
                              true)
                           vastaus)
        eh-summa (apply + (map #(:toteutunut_summa %) erillishankinnat))
        erillishankinnat-sql (q (erilliskustannusten-toteumat-sql-haku urakka-id alkupvm loppupvm hoitokauden-alkuvuosi))
        sql-summa (apply + (map #(first %) erillishankinnat-sql))]
    (is (= eh-summa sql-summa))))

;; Testataan/vertaillaan Hoidonjohdonpalkkioiden budjetoituja summia
(deftest budjetoidut-hoidonjohdonpalkkiot-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        hoitokauden-alkuvuosi 2019
        vastaus (hae-kustannukset urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)
        hj_palkkiot (filter
                      #(when (= "hoidonjohdonpalkkio" (:paaryhma %))
                         true)
                      vastaus)
        hj-summa (apply + (map #(:budjetoitu_summa %) hj_palkkiot))
        hj-sql (q (hoidonjohdonpalkkio-budjetoidut-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) hj-sql))]
    (is (= hj-summa sql-summa))))

;; Testataan/vertaillaan Hoidonjohdonpalkkioiden toteutuneita summia
(deftest toteutuneet-hoidonjohdonpalkkiot-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        hoitokauden-alkuvuosi 2019
        vastaus (hae-kustannukset urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)
        hj_palkkiot (filter
                      #(when (= "hoidonjohdonpalkkio" (:paaryhma %))
                         true)
                      vastaus)
        hj-summa (apply + (map #(:toteutunut_summa %) hj_palkkiot))
        hj-sql (q (hoidonjohdonpalkkio-toteumat-sql-haku urakka-id alkupvm loppupvm hoitokauden-alkuvuosi))
        sql-summa (apply + (map #(first %) hj-sql))]
    (is (= hj-summa sql-summa))))

;; Testataan/vertaillaan Johto- ja hallintokorvauksen budjetoituja summia
;; Johto- ja hallintokorvaukset kuuluu palkat ja muut kulut, kuten toimistokulut yms.
(deftest budjetoidut-johtoja-hallintokorvaukset-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        hoitokauden-alkuvuosi 2019
        vastaus (hae-kustannukset urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)
        johto_ja_h_korvaukset (filter
                                #(when (= "johto-ja-hallintakorvaus" (:paaryhma %))
                                   true)
                                vastaus)
        jjh-summa (apply + (map #(:budjetoitu_summa %) johto_ja_h_korvaukset))
        jjh-sql (q (johto-ja-hallintokorvaukset-budjetoidut-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) jjh-sql))]
    (is (= jjh-summa sql-summa))))

;; Testataan/vertaillaan Johto- ja hallintokorvauksen toteutuneita summia
(deftest toteutuneet-johtoja-hallintokorvaukset-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        hoitokauden-alkuvuosi 2019
        vastaus (hae-kustannukset urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)
        johto_ja_h_korvaukset (filter
                                #(when (= "johto-ja-hallintakorvaus" (:paaryhma %))
                                   true)
                                vastaus)
        jjh-summa (apply + (map #(:toteutunut_summa %) johto_ja_h_korvaukset))
        jjh-sql (q (johto-ja-hallintokorvaukset-toteutuneet-sql-haku urakka-id alkupvm loppupvm hoitokauden-alkuvuosi))
        sql-summa (apply + (map #(first %) jjh-sql))]
    (is (= jjh-summa sql-summa))))

;; Testataan/vertaillaan Hankintakustannusten budjetoituja summia
;; Hankintakustannusten alle tulee toimenpideryhmät kuten talvihoito, liikenneympäristöhoito, yms.
(deftest budjetoidut-hankintakustannukset-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        hoitokauden-alkuvuosi 2019
        vastaus (hae-kustannukset urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)

        hankintakustannukset (filter
                               #(when (and
                                        (= "hankintakustannukset" (:paaryhma %))
                                        (not= "lisatyo" (:toimenpideryhma %)))
                                  true)
                               vastaus)
        hankintakustannukset-budjetoitu (apply + (map #(:budjetoitu_summa %) hankintakustannukset))
        h-sql (q (hankintakustannukset-budjetoitu-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) h-sql))]
    (is (= hankintakustannukset-budjetoitu sql-summa))))

;; Testataan/vertaillaan Hankintakustannusten toteutuneita summia
;; Hankintakustannusten alle tulee toimenpideryhmät kuten talvihoito, liikenneympäristöhoito, yms.
(deftest toteutuneet-hankintakustannukset-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        hoitokauden-alkuvuosi 2019
        vastaus (hae-kustannukset urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)
        hankintakustannukset (filter
                               #(when (= "hankintakustannukset" (:paaryhma %))
                                  true)
                               vastaus)
        ;; Filtteröidään vielä lisätyöt pois
        h-summa (apply + (map (fn [rivi]
                                (if (not= "lisatyo" (:toimenpideryhma rivi))
                                  (:toteutunut_summa rivi)
                                  0))
                           hankintakustannukset))
        h-sql (q (hankintakustannukset-toteutuneet-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) h-sql))]
    (is (= h-summa sql-summa))))

;; Testataan/vertaillaan toteutuneita lisätöitä
;; Lisätöitä ei voi suunnitella etukäteen, joten niille ei ole budjetoituja kustannuksia olemassa.
;; Tallennetaan laskulla lisätyö Johto ja halllintokrvaukselle, koska se on monimutkaisin lisätyötyyppi
(defn- uusi-lasku [urakka-id summa]
  {:id nil
   :urakka urakka-id
   :viite "6666668"
   :erapaiva #inst "2020-08-15T21:00:00.000-00:00"
   :kokonaissumma 1332
   :tyyppi "laskutettava"
   :kohdistukset [{:kohdistus-id nil
                   :rivi 1
                   :summa summa
                   :suoritus-alku #inst "2020-08-14T22:00:00.000000000-00:00"
                   :suoritus-loppu #inst "2020-08-17T22:00:00.000000000-00:00"
                   :toimenpideinstanssi 48
                   :tehtavaryhma nil
                   :lisatyo? true
                   :tehtava nil}]
   :koontilaskun-kuukausi "elokuu/1-hoitovuosi"})

(deftest lisatyot-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        urakkavastaava (oulun-2019-urakan-urakoitsijan-urakkavastaava)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        hoitokauden-alkuvuosi 2019
        laskun-summa 105.01
        ;; Lisää uusi lasku listyöstä johto-ja hallintakorvaukselle
        _ (kutsu-http-palvelua :tallenna-lasku urakkavastaava
                                   {:urakka-id urakka-id
                                    :laskuerittely (uusi-lasku urakka-id laskun-summa)})

        vastaus (hae-kustannukset urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)
        lisatyot (filter
                   #(when (= "lisatyo" (:maksutyyppi %))
                      true)
                   vastaus)
        luotu-lisatyo (filter
                           #(when (= (bigdec laskun-summa) (:toteutunut_summa %))
                              true)
                           lisatyot)
        lisatyot-summa (apply + (map #(:toteutunut_summa %) lisatyot))
        lisatyot-sql (q (lisatyot-sql-haku urakka-id alkupvm loppupvm))
        sql-summa (apply + (map #(first %) lisatyot-sql))]
    ;; Vertaillaan summaa
    (is (= lisatyot-summa sql-summa))
    (is (= (count luotu-lisatyo) 1))))

;; Budjetoidut bonukset
(deftest bonukset-test
  (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        hoitokauden-alkuvuosi 2019
        vastaus (hae-kustannukset urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)
        bonukset (filter
                   #(when (= "bonukset" (:paaryhma %))
                      true)
                   vastaus)
        ;; Filtteröidään vielä lisätyöt pois
        bonus-toteutuneet (apply + (map (fn [rivi]
                                (if (not= "lisatyo" (:toimenpideryhma rivi))
                                  (:toteutunut_summa rivi)
                                  0))
                                        bonukset))
        bonus-budjetoitu (apply + (map (fn [rivi]
                                    (if (not= "lisatyo" (:toimenpideryhma rivi))
                                      (:budjetoitu_summa rivi)
                                      0))
                                       bonukset))
        bonukset-toteutuneet-sql (q (bonukset-toteutuneet-sql-haku urakka-id alkupvm loppupvm hoitokauden-alkuvuosi))
        bonukset-budjetoitu-sql (q (bonukset-budjetoitu-sql-haku urakka-id alkupvm loppupvm hoitokauden-alkuvuosi))
        bonukset-sql-toteutunut-summa (apply + (map #(first %) bonukset-toteutuneet-sql))
        bonukset-sql-budjetoitu-summa (apply + (map #(first %) bonukset-budjetoitu-sql))]
    (is (= bonus-toteutuneet bonukset-sql-toteutunut-summa))
    (is (= bonus-budjetoitu bonukset-sql-budjetoitu-summa))))

;; Testataan, että backendistä voidaan kutsua excelin luontia ja excel ladataan.
;; Excelin sisältöä ei valitoida
#_(deftest excel-render-test
    (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          alkupvm "2019-10-01"
          loppupvm "2020-09-30"
          hoitokauden-alkuvuosi 2019
          vastaus (lataa-excel urakka-id hoitokauden-alkuvuosi alkupvm loppupvm)
          _ (println "excel-render-test :: vastaus" (pr-str vastaus))
          ]
      ))

;; TODO: Tee erillinen urakka, jolle syötetään suunnitelmat ja kulut ja tarkista, että kaikki löytyy tietokannasta oikein