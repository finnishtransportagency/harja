<<<<<<< HEAD
ALTER TABLE integraatioviesti ADD COLUMN osoite VARCHAR;
=======
CREATE TYPE laskutusyhteenveto_rivi
AS (nimi            VARCHAR,
    kht_laskutettu  NUMERIC, kht_laskutettu_ind_korotettuna NUMERIC, kht_laskutettu_ind_korotus NUMERIC,
    kht_laskutetaan NUMERIC, kht_laskutetaan_ind_korotettuna NUMERIC, kht_laskutetaan_ind_korotus NUMERIC,
    yht_laskutettu  NUMERIC, yht_laskutettu_ind_korotettuna NUMERIC, yht_laskutettu_ind_korotus NUMERIC,
    yht_laskutetaan NUMERIC, yht_laskutetaan_ind_korotettuna NUMERIC, yht_laskutetaan_ind_korotus NUMERIC);

CREATE OR REPLACE FUNCTION laskutusyhteenveto(
  hk_alkupvm DATE, hk_loppupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
  ur         INTEGER)
  RETURNS SETOF laskutusyhteenveto_rivi AS $$
DECLARE
  t                               RECORD;
  kht_laskutettu                  NUMERIC;
  kht_laskutettu_ind_korotettuna  NUMERIC;
  kht_laskutettu_ind_korotus      NUMERIC;
  kht_laskutetaan                 NUMERIC;
  kht_laskutetaan_ind_korotettuna NUMERIC;
  kht_laskutetaan_ind_korotus     NUMERIC;
  khti                            RECORD;
  aikavalin_kht                   RECORD;
  kht_laskutetaan_rivi            kuukauden_indeksikorotus_rivi;

  yht_laskutettu                  NUMERIC;
  yht_laskutettu_ind_korotettuna  NUMERIC;
  yht_laskutettu_ind_korotus      NUMERIC;
  yht_laskutettu_rivi             kuukauden_indeksikorotus_rivi;
  yht_laskutetaan                 NUMERIC;
  yht_laskutetaan_ind_korotettuna NUMERIC;
  yht_laskutetaan_ind_korotus     NUMERIC;
  yht_laskutetaan_rivi            kuukauden_indeksikorotus_rivi;
  yhti                            RECORD;
  yhti_laskutetaan                RECORD;

BEGIN
  -- Kerroin on ko. indeksin arvo ko. kuukautena ja vuonna
  FOR t IN SELECT
             tpk2.nimi AS nimi,
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

    yht_laskutettu := 0.0;
    yht_laskutettu_ind_korotettuna := 0.0;
    yht_laskutettu_ind_korotus := 0.0;

    RAISE NOTICE 'Lasketaan urakan % kokonaishintaisten töiden kustannukset hoitokaudella % - %', ur, hk_alkupvm, hk_loppupvm;

    -- Hoitokaudella ennen aikaväliä laskutetut kokonaishintaisten töiden kustannukset, myös indeksitarkistuksen kanssa
    FOR khti IN SELECT
                  (SELECT korotus
                   FROM laske_kuukauden_indeksikorotus(kht.vuosi, kht.kuukausi, 'MAKU 2010',
                                                       kht.summa)) AS ind,
                  (SELECT korotettuna
                   FROM laske_kuukauden_indeksikorotus(kht.vuosi, kht.kuukausi, 'MAKU 2010',
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

    SELECT *
    FROM laske_kuukauden_indeksikorotus(aikavalin_kht.vuosi, aikavalin_kht.kuukausi, 'MAKU 2010', aikavalin_kht.summa)
    INTO kht_laskutetaan_rivi;

    -- Hoitokaudella ennen aikaväliä laskutetut yksikköhintaisten töiden kustannukset, myös indeksitarkistuksen kanssa
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
                                          'MAKU 2010', yhti.yht_summa)
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
                                        'MAKU 2010', yhti_laskutetaan.yht_summa)
    INTO yht_laskutetaan_rivi;

    yht_laskutetaan_ind_korotettuna := yht_laskutetaan_rivi.korotettuna;
    yht_laskutetaan_ind_korotus := yht_laskutetaan_rivi.korotus;

    RETURN NEXT (t.nimi,
                 kht_laskutettu, kht_laskutettu_ind_korotettuna, kht_laskutettu_ind_korotus,
                 aikavalin_kht.summa, kht_laskutetaan_rivi.korotettuna, kht_laskutetaan_rivi.korotus,
                 yht_laskutettu, yht_laskutettu_ind_korotettuna, yht_laskutettu_ind_korotus,
                 yht_laskutetaan, yht_laskutetaan_ind_korotettuna, yht_laskutetaan_ind_korotus);
  END LOOP;

END;
$$ LANGUAGE plpgsql;
>>>>>>> develop
