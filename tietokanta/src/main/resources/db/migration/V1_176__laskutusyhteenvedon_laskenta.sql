-- Hakee ja laskee tietoja kannasta laskutusyhteenvetoa varten
DROP FUNCTION laskutusyhteenveto(hk_alkupvm DATE, hk_loppupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE, ur INTEGER );
DROP TYPE laskutusyhteenveto_rivi;


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

  yht_laskutettu                  NUMERIC;
  yht_laskutettu_ind_korotettuna  NUMERIC;
  yht_laskutettu_ind_korotus      NUMERIC;
  yht_laskutetaan                 NUMERIC;
  yht_laskutetaan_ind_korotettuna NUMERIC;
  yht_laskutetaan_ind_korotus     NUMERIC;
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

    yht_laskutettu := 0.0;
    yht_laskutettu_ind_korotettuna := 0.0;

    RAISE NOTICE 'Lasketaan urakan % kokonaishintaisten töiden kustannukset hoitokaudella % - %', ur, hk_alkupvm, hk_loppupvm;

    -- Hoitokaudella ennen aikaväliä laskutetut kokonaishintaisten töiden kustannukset, myös indeksitarkistuksen kanssa
    FOR khti IN SELECT
                  (SELECT korotus
                   FROM kuukauden_indeksikorotus(kht.vuosi, kht.kuukausi, 'MAKU 2010',
                                                 kht.summa)) AS ind,
                  kht.summa                                  AS kht_summa
                FROM kokonaishintainen_tyo kht
                WHERE toimenpideinstanssi = t.tpi
                      AND maksupvm >= hk_alkupvm
                      AND maksupvm <= hk_loppupvm
                      AND maksupvm < aikavali_alkupvm LOOP
      kht_laskutettu := kht_laskutettu + khti.kht_summa;
      kht_laskutettu_ind_korotettuna := kht_laskutettu_ind_korotettuna + khti.ind;
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
    SELECT kuukauden_indeksikorotus(to_date(aikavalin_kht.vuosi || '/' || aikavalin_kht.kuukausi || '/1', 'YYYY/MM/DD'),
                                    'MAKU 2010', aikavalin_kht.summa)
    INTO kht_laskutetaan_ind_korotettuna
    FROM kokonaishintainen_tyo
    WHERE toimenpideinstanssi = t.tpi
          AND maksupvm >= hk_alkupvm
          AND maksupvm <= hk_loppupvm
          AND maksupvm >= aikavali_alkupvm
          AND maksupvm <= aikavali_loppupvm;

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
      yht_laskutettu := yht_laskutettu + yhti.yht_summa;
      yht_laskutettu_ind_korotettuna := yht_laskutettu_ind_korotettuna +
                                        kuukauden_indeksikorotus(yhti.tot_alkanut :: DATE, 'MAKU 2010', yhti.yht_summa);


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

    yht_laskutetaan_ind_korotettuna := kuukauden_indeksikorotus(yhti_laskutetaan.tot_alkanut :: DATE, 'MAKU 2010',
                                                                yhti_laskutetaan.yht_summa);

    RETURN NEXT (t.nimi,
                 kht_laskutettu, kht_laskutettu_ind_korotettuna, kht_laskutettu_ind_korotus,
                 aikavalin_kht.summa, kht_laskutetaan_ind_korotettuna, kht_laskutetaan_ind_korotus,
                 yht_laskutettu, yht_laskutettu_ind_korotettuna, yht_laskutettu_ind_korotus,
                 yht_laskutetaan, yht_laskutetaan_ind_korotettuna, yht_laskutetaan_ind_korotus);
  END LOOP;

END;
$$ LANGUAGE plpgsql;


-- ^ yllä tuotantokoodi ^
-- Alla helppereitä

SELECT *
FROM laskutusyhteenveto('2014-10-01', '2015-09-30', '2015-07-01', '2015-07-31', 4);
