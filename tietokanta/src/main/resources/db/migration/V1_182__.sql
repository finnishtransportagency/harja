-- Kuvaus: Laskutusyhteenvedon laskenta
--DROP FUNCTION laskutusyhteenveto(hk_alkupvm DATE, hk_loppupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE, ur INTEGER);
DROP FUNCTION laskutusyhteenveto(hk_alkupvm DATE, hk_loppupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE, ur INTEGER, ind VARCHAR(128));
 
DROP TYPE laskutusyhteenveto_rivi;

CREATE TYPE laskutusyhteenveto_rivi
AS (nimi            VARCHAR, tuotekoodi VARCHAR,
  kht_laskutettu  NUMERIC, kht_laskutettu_ind_korotettuna NUMERIC, kht_laskutettu_ind_korotus NUMERIC,
  kht_laskutetaan NUMERIC, kht_laskutetaan_ind_korotettuna NUMERIC, kht_laskutetaan_ind_korotus NUMERIC,
  yht_laskutettu  NUMERIC, yht_laskutettu_ind_korotettuna NUMERIC, yht_laskutettu_ind_korotus NUMERIC,
  yht_laskutetaan NUMERIC, yht_laskutetaan_ind_korotettuna NUMERIC, yht_laskutetaan_ind_korotus NUMERIC,
  sakot_laskutettu NUMERIC, sakot_laskutettu_ind_korotettuna NUMERIC, sakot_laskutettu_ind_korotus NUMERIC,
  sakot_laskutetaan NUMERIC, sakot_laskutetaan_ind_korotettuna NUMERIC, sakot_laskutetaan_ind_korotus NUMERIC,
  suolasakot_laskutettu NUMERIC, suolasakot_laskutettu_ind_korotettuna NUMERIC, suolasakot_laskutettu_ind_korotus NUMERIC,
  suolasakot_laskutetaan NUMERIC, suolasakot_laskutetaan_ind_korotettuna NUMERIC, suolasakot_laskutetaan_ind_korotus NUMERIC);

CREATE OR REPLACE FUNCTION laskutusyhteenveto(
  hk_alkupvm DATE, hk_loppupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
  ur         INTEGER, ind VARCHAR(128))
  RETURNS SETOF laskutusyhteenveto_rivi AS $$
DECLARE
  t                                      RECORD;
  kht_laskutettu                         NUMERIC;
  kht_laskutettu_ind_korotettuna         NUMERIC;
  kht_laskutettu_ind_korotus             NUMERIC;
  kht_laskutetaan                        NUMERIC;
  kht_laskutetaan_ind_korotettuna        NUMERIC;
  kht_laskutetaan_ind_korotus            NUMERIC;
  khti                                   RECORD;
  aikavalin_kht                          RECORD;
  kht_laskutetaan_rivi                   kuukauden_indeksikorotus_rivi;

  yht_laskutettu                         NUMERIC;
  yht_laskutettu_ind_korotettuna         NUMERIC;
  yht_laskutettu_ind_korotus             NUMERIC;
  yht_laskutettu_rivi                    kuukauden_indeksikorotus_rivi;
  yht_laskutetaan                        NUMERIC;
  yht_laskutetaan_ind_korotettuna        NUMERIC;
  yht_laskutetaan_ind_korotus            NUMERIC;
  yht_laskutetaan_rivi                   kuukauden_indeksikorotus_rivi;
  yhti                                   RECORD;
  yhti_laskutetaan                       RECORD;

  sakot_laskutettu                       NUMERIC;
  sakot_laskutettu_ind_korotettuna       NUMERIC;
  sakot_laskutettu_ind_korotus           NUMERIC;
  sakot_laskutettu_rivi                  RECORD;

  sakot_laskutetaan                      NUMERIC;
  sakot_laskutetaan_ind_korotettuna      NUMERIC;
  sakot_laskutetaan_ind_korotus          NUMERIC;
  sakot_laskutetaan_rivi                 RECORD;
  sanktiorivi                            RECORD;
  aikavalin_sanktio                      RECORD;

  suolasakot_laskutettu                  NUMERIC;
  suolasakot_laskutettu_ind_korotettuna  NUMERIC;
  suolasakot_laskutettu_ind_korotus      NUMERIC;
  suolasakot_laskutettu_rivi             RECORD;

  suolasakot_laskutetaan                 NUMERIC;
  suolasakot_laskutetaan_ind_korotettuna NUMERIC;
  suolasakot_laskutetaan_ind_korotus     NUMERIC;
  suolasakot_laskutetaan_rivi            RECORD;
  hoitokauden_suolasakko_rivi            RECORD;
  hoitokauden_laskettu_suolasakko_rivi            indeksitarkistettu_suolasakko_rivi;
  hoitokauden_laskettu_suolasakon_maara  NUMERIC;

BEGIN
  -- Kerroin on ko. indeksin arvo ko. kuukautena ja vuonna
  FOR t IN SELECT
             tpk2.nimi AS nimi,
             tpk2.koodi AS tuotekoodi,
             tpi.id    AS tpi,
             tpk3.id   AS tpk3_id
           FROM toimenpideinstanssi tpi
             JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
             JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id
           WHERE tpi.urakka = ur
  LOOP
    kht_laskutettu := 0.0;
    kht_laskutettu_ind_korotettuna := 0.0;
    kht_laskutettu_ind_korotus := 0.0;

    -- Hoitokaudella ennen aikaväliä laskutetut kokonaishintaisten töiden kustannukset, myös indeksitarkistuksen kanssa
    FOR khti IN SELECT
                  (SELECT korotus
                   FROM laske_kuukauden_indeksikorotus(kht.vuosi, kht.kuukausi, ind,
                                                       kht.summa)) AS ind,
                  (SELECT korotettuna
                   FROM laske_kuukauden_indeksikorotus(kht.vuosi, kht.kuukausi, ind,
                                                       kht.summa)) AS kor,
                  kht.summa                                        AS kht_summa
                FROM kokonaishintainen_tyo kht
                WHERE toimenpideinstanssi = t.tpi
                      AND maksupvm >= hk_alkupvm
                      AND maksupvm <= hk_loppupvm
                      AND maksupvm < aikavali_alkupvm LOOP
      kht_laskutettu := kht_laskutettu + khti.kht_summa;
      kht_laskutettu_ind_korotettuna := kht_laskutettu_ind_korotettuna + khti.kor;
      kht_laskutettu_ind_korotus := kht_laskutettu_ind_korotus + khti.ind;
    END LOOP;

    -- Kokonaishintaiset aikavälillä
    SELECT
      summa,
      vuosi,
      kuukausi
    INTO aikavalin_kht
    FROM kokonaishintainen_tyo
    WHERE toimenpideinstanssi = t.tpi
          AND maksupvm >= hk_alkupvm
          AND maksupvm <= hk_loppupvm
          AND maksupvm >= aikavali_alkupvm
          AND maksupvm <= aikavali_loppupvm;

    -- Kokonaishintaiset aikavälillä indeksikorotuksen kanssa
    kht_laskutetaan := 0.0;
    kht_laskutetaan_ind_korotettuna := 0.0;
    kht_laskutetaan_ind_korotus := 0.0;

    SELECT *
    FROM laske_kuukauden_indeksikorotus(aikavalin_kht.vuosi, aikavalin_kht.kuukausi, ind, aikavalin_kht.summa)
    INTO kht_laskutetaan_rivi;

    kht_laskutetaan := COALESCE(kht_laskutetaan_rivi.summa, 0.0);
    kht_laskutetaan_ind_korotettuna := COALESCE(kht_laskutetaan_rivi.korotettuna, kht_laskutetaan);
    kht_laskutetaan_ind_korotus := COALESCE(kht_laskutetaan_rivi.korotus, 0.0);

    -- Hoitokaudella ennen aikaväliä laskutetut yksikköhintaisten töiden kustannukset, myös indeksitarkistuksen kanssa
    yht_laskutettu := 0.0;
    yht_laskutettu_ind_korotettuna := 0.0;
    yht_laskutettu_ind_korotus := 0.0;

    FOR yhti IN SELECT
                  SUM(tt.maara * yht.yksikkohinta) AS yht_summa,
                  tot.alkanut                      AS tot_alkanut
                FROM toteuma_tehtava tt
                  JOIN toteuma tot ON tt.toteuma = tot.id
                  JOIN toimenpidekoodi tpk4 ON tt.toimenpidekoodi = tpk4.id
                  JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
                  JOIN yksikkohintainen_tyo yht ON (tt.toimenpidekoodi = yht.tehtava
                                                    AND yht.alkupvm <= tot.alkanut AND yht.loppupvm >= tot.paattynyt
                                                    AND tpk3.id = t.tpk3_id)
                WHERE yht.urakka = ur
                      AND tot.urakka = ur
                      AND tot.alkanut >= hk_alkupvm AND tot.alkanut <= hk_loppupvm
                      AND tot.alkanut <= aikavali_alkupvm AND tot.paattynyt <= aikavali_alkupvm
                GROUP BY tot.alkanut
    LOOP
      SELECT *
      FROM laske_kuukauden_indeksikorotus((SELECT EXTRACT(YEAR FROM yhti.tot_alkanut) :: INTEGER),
                                          (SELECT EXTRACT(MONTH FROM yhti.tot_alkanut) :: INTEGER),
                                          ind, yhti.yht_summa)
      INTO yht_laskutettu_rivi;
      yht_laskutettu :=  yht_laskutettu + yht_laskutettu_rivi.summa;
      yht_laskutettu_ind_korotettuna :=  yht_laskutettu_ind_korotettuna + yht_laskutettu_rivi.korotettuna;
      yht_laskutettu_ind_korotus :=  yht_laskutettu_ind_korotus + yht_laskutettu_rivi.korotus;


    END LOOP;

    -- Aikavälillä laskutettavat yksikköhintaisten töiden kustannukset indeksitarkistuksen kanssa
    SELECT SUM(tt.maara * yht.yksikkohinta)
    INTO yht_laskutetaan
    FROM toteuma_tehtava tt
      JOIN toteuma tot ON tt.toteuma = tot.id
      JOIN toimenpidekoodi tpk4 ON tt.toimenpidekoodi = tpk4.id
      JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
      JOIN yksikkohintainen_tyo yht ON (tt.toimenpidekoodi = yht.tehtava
                                        AND yht.alkupvm <= tot.alkanut AND yht.loppupvm >= tot.paattynyt
                                        AND tpk3.id = t.tpk3_id)
    WHERE yht.urakka = ur
          AND tot.urakka = ur
          AND tot.alkanut >= hk_alkupvm AND tot.alkanut <= hk_loppupvm
          AND tot.alkanut >= aikavali_alkupvm AND tot.alkanut <= aikavali_loppupvm
          AND tot.paattynyt >= aikavali_alkupvm AND tot.paattynyt <= aikavali_loppupvm;

    -- Aikavälillä laskutettavat yksikköhintaisten töiden kustannukset indeksitarkistuksen kanssa
    SELECT
      tot.alkanut                      AS tot_alkanut,
      SUM(tt.maara * yht.yksikkohinta) AS yht_summa
    INTO yhti_laskutetaan
    FROM toteuma_tehtava tt
      JOIN toteuma tot ON tt.toteuma = tot.id
      JOIN toimenpidekoodi tpk4 ON tt.toimenpidekoodi = tpk4.id
      JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
      JOIN yksikkohintainen_tyo yht ON (tt.toimenpidekoodi = yht.tehtava
                                        AND yht.alkupvm <= tot.alkanut AND yht.loppupvm >= tot.paattynyt
                                        AND tpk3.id = t.tpk3_id)
    WHERE yht.urakka = ur
          AND tot.urakka = ur
          AND tot.alkanut >= hk_alkupvm AND tot.alkanut <= hk_loppupvm
          AND tot.alkanut >= aikavali_alkupvm AND tot.alkanut <= aikavali_loppupvm
          AND tot.paattynyt >= aikavali_alkupvm AND tot.paattynyt <= aikavali_loppupvm
    GROUP BY tot.alkanut;

    SELECT *
    FROM laske_kuukauden_indeksikorotus((SELECT EXTRACT(YEAR FROM yhti_laskutetaan.tot_alkanut) :: INTEGER),
                                        (SELECT EXTRACT(MONTH FROM yhti_laskutetaan.tot_alkanut) :: INTEGER),
                                        ind, yhti_laskutetaan.yht_summa)
    INTO yht_laskutetaan_rivi;

    yht_laskutetaan := COALESCE(yht_laskutetaan, 0.0);
    yht_laskutetaan_ind_korotettuna := COALESCE(yht_laskutetaan_rivi.korotettuna, yht_laskutetaan);
    yht_laskutetaan_ind_korotus := COALESCE(yht_laskutetaan_rivi.korotus, 0.0);


    -- Hoitokaudella ennen aikaväliä laskutetut sanktiot
    sakot_laskutettu := 0.0;
    sakot_laskutettu_ind_korotettuna := 0.0;
    sakot_laskutettu_ind_korotus := 0.0;

    FOR sanktiorivi IN SELECT
                         maara,
                         perintapvm,
                         indeksi
                       FROM sanktio s
                       WHERE s.toimenpideinstanssi = t.tpi
                             AND s.perintapvm >= hk_alkupvm
                             AND s.perintapvm <= hk_loppupvm
                             AND s.perintapvm < aikavali_alkupvm
    LOOP

      SELECT *
      FROM laske_kuukauden_indeksikorotus((SELECT EXTRACT(YEAR FROM sanktiorivi.perintapvm) :: INTEGER),
                                          (SELECT EXTRACT(MONTH FROM sanktiorivi.perintapvm) :: INTEGER),
                                          sanktiorivi.indeksi,
                                          sanktiorivi.maara)
      INTO sakot_laskutettu_rivi;
      sakot_laskutettu := sakot_laskutettu + COALESCE(sakot_laskutettu_rivi.summa, 0.0);
      sakot_laskutettu_ind_korotettuna := sakot_laskutettu_ind_korotettuna + COALESCE(sakot_laskutettu_rivi.korotettuna, sakot_laskutettu_rivi.summa);
      sakot_laskutettu_ind_korotus := sakot_laskutettu_ind_korotus + COALESCE(sakot_laskutettu_rivi.korotus, 0.0);


    END LOOP;


    -- Sanktiot aikavälillä
    sakot_laskutetaan := 0.0;
    sakot_laskutetaan_ind_korotettuna := 0.0;
    sakot_laskutetaan_ind_korotus := 0.0;

    FOR sanktiorivi IN SELECT
                         maara,
                         perintapvm,
                         indeksi
                       FROM sanktio s
                       WHERE s.toimenpideinstanssi = t.tpi
                             AND s.perintapvm >= hk_alkupvm
                             AND s.perintapvm <= hk_loppupvm
                             AND s.perintapvm >= aikavali_alkupvm
                             AND s.perintapvm <= aikavali_loppupvm
    LOOP

      SELECT *
      FROM laske_kuukauden_indeksikorotus((SELECT EXTRACT(YEAR FROM sanktiorivi.perintapvm) :: INTEGER),
                                          (SELECT EXTRACT(MONTH FROM sanktiorivi.perintapvm) :: INTEGER),
                                          sanktiorivi.indeksi,
                                          sanktiorivi.maara)
      INTO sakot_laskutetaan_rivi;
      sakot_laskutetaan := sakot_laskutetaan + sakot_laskutetaan_rivi.summa;
      sakot_laskutetaan_ind_korotettuna := sakot_laskutetaan_ind_korotettuna + COALESCE(sakot_laskutetaan_rivi.korotettuna, sakot_laskutetaan_rivi.summa);
      sakot_laskutetaan_ind_korotus := sakot_laskutetaan_ind_korotus + COALESCE(sakot_laskutetaan_rivi.korotus, 0.0);
    END LOOP;

    suolasakot_laskutettu := 0.0;
    suolasakot_laskutettu_ind_korotettuna := 0.0;
    suolasakot_laskutettu_ind_korotus := 0.0;
    suolasakot_laskutetaan := 0.0;
    suolasakot_laskutetaan_ind_korotettuna := 0.0;
    suolasakot_laskutetaan_ind_korotus := 0.0;

    SELECT *
    FROM suolasakko
    WHERE urakka = ur
          AND (SELECT EXTRACT(YEAR FROM hk_alkupvm) :: INTEGER) = hoitokauden_alkuvuosi
    INTO hoitokauden_suolasakko_rivi;

    hoitokauden_laskettu_suolasakon_maara = (SELECT hoitokauden_suolasakko(ur, hk_alkupvm, hk_loppupvm));


    -- Suolasakko lasketaan vain Talvihoito-toimenpiteelle (tuotekoodi '23100')
   IF t.tuotekoodi = '23100' THEN
      SELECT *
      FROM laske_suolasakon_indeksitarkistus(hoitokauden_suolasakko_rivi.hoitokauden_alkuvuosi,
                                             hoitokauden_suolasakko_rivi.indeksi,
                                             hoitokauden_laskettu_suolasakon_maara)
      INTO hoitokauden_laskettu_suolasakko_rivi;

      IF hoitokauden_suolasakko_rivi.maksukuukausi < (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER) THEN
        RAISE NOTICE 'Suolasakko on laskutettu aiemmin hoitokaudella kuukautena %', hoitokauden_suolasakko_rivi.maksukuukausi;
        suolasakot_laskutettu := hoitokauden_laskettu_suolasakko_rivi.summa;
        suolasakot_laskutettu_ind_korotettuna := hoitokauden_laskettu_suolasakko_rivi.korotettuna;
        suolasakot_laskutettu_ind_korotus := COALESCE(hoitokauden_laskettu_suolasakko_rivi.korotus, 0.0);
      ELSIF hoitokauden_suolasakko_rivi.maksukuukausi = (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER) THEN
        RAISE NOTICE 'Suolasakko laskutetaan tässä kuussa %', hoitokauden_suolasakko_rivi.maksukuukausi;
        suolasakot_laskutetaan := hoitokauden_laskettu_suolasakko_rivi.summa;
        suolasakot_laskutetaan_ind_korotettuna := hoitokauden_laskettu_suolasakko_rivi.korotettuna;
        suolasakot_laskutetaan_ind_korotus := COALESCE(hoitokauden_laskettu_suolasakko_rivi.korotus, 0.0);
      ELSE
        RAISE NOTICE 'Suolasakkoa ei vielä laskutettu, maksukuukauden arvo: %', hoitokauden_suolasakko_rivi.maksukuukausi;
      END IF;
   END IF;

      RETURN NEXT (t.nimi, t.tuotekoodi,
                   kht_laskutettu, kht_laskutettu_ind_korotettuna, kht_laskutettu_ind_korotus,
                   kht_laskutetaan, kht_laskutetaan_ind_korotettuna, kht_laskutetaan_ind_korotus,
                   yht_laskutettu, yht_laskutettu_ind_korotettuna, yht_laskutettu_ind_korotus,
                   yht_laskutetaan, yht_laskutetaan_ind_korotettuna, yht_laskutetaan_ind_korotus,
                   sakot_laskutettu, sakot_laskutettu_ind_korotettuna, sakot_laskutettu_ind_korotus,
                   sakot_laskutetaan, sakot_laskutetaan_ind_korotettuna, sakot_laskutetaan_ind_korotus,
                   suolasakot_laskutettu, suolasakot_laskutettu_ind_korotettuna, suolasakot_laskutettu_ind_korotus,
                   suolasakot_laskutetaan, suolasakot_laskutetaan_ind_korotettuna, suolasakot_laskutetaan_ind_korotus);


    END LOOP;

  END;
$$ LANGUAGE plpgsql;


SELECT * FROM laskutusyhteenveto('2014-10-01', '2015-09-30', '2015-07-01', '2015-07-31', 4, 'MAKU 2010');
SELECT * FROM laskutusyhteenveto('2014-10-01', '2015-09-30', '2015-08-01', '2015-08-31', 4, 'MAKU 2010');
SELECT * FROM laskutusyhteenveto('2014-10-01', '2015-09-30', '2015-09-01', '2015-09-30', 4, 'MAKU 2010');


select * from sanktio;
select * from suolasakko;
SELECT hoitokauden_suolasakko(4, '2014-10-01', '2015-09-30');
SELECT laske_suolasakko(2014, 'MAKU 2010' ,2280.00);

DECLARE  hoitokauden_suolasakko_rivi RECORD;


SELECT *
FROM suolasakko
WHERE urakka = 4
      AND (SELECT EXTRACT(YEAR FROM '2014-10-01'::DATE) :: INTEGER) = 2014;

(SELECT EXTRACT(YEAR FROM '2014-10-01') :: INTEGER);
