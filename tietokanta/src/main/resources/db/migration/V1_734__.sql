CREATE OR REPLACE FUNCTION laskutusyhteenveto (hk_alkupvm date, hk_loppupvm date, aikavali_alkupvm date, aikavali_loppupvm date, ur integer) RETURNS SETOF laskutusyhteenveto_rivi
AS $$
DECLARE
  t                                      RECORD;
  ind VARCHAR; -- hoitourakassa käytettävä indeksi
  perusluku NUMERIC; -- urakan indeksilaskennan perusluku (urakkasopimusta edeltävän vuoden joulukuusta 3kk ka)

  kaikki_paitsi_kht_laskutettu_ind_korotus NUMERIC;
  kaikki_laskutettu_ind_korotus NUMERIC;
  kaikki_paitsi_kht_laskutetaan_ind_korotus NUMERIC;
  kaikki_laskutetaan_ind_korotus NUMERIC;

  kaikki_paitsi_kht_laskutettu NUMERIC;
  kaikki_laskutettu NUMERIC;
  kaikki_paitsi_kht_laskutetaan NUMERIC;
  kaikki_laskutetaan NUMERIC;

  kht_laskutettu                         NUMERIC;
  kht_laskutettu_ind_korotettuna         NUMERIC;
  kht_laskutettu_ind_korotus             NUMERIC;
  kht_laskutetaan                        NUMERIC;
  kht_laskutetaan_ind_korotettuna        NUMERIC;
  kht_laskutetaan_ind_korotus            NUMERIC;
  khti                                   RECORD;
  khti_laskutetaan                       RECORD;
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
  yht_rivi                               RECORD;

  sakot_laskutettu                       NUMERIC;
  sakot_laskutettu_ind_korotettuna       NUMERIC;
  sakot_laskutettu_ind_korotus           NUMERIC;
  sakot_rivi                             RECORD;

  sakot_laskutetaan                      NUMERIC;
  sakot_laskutetaan_ind_korotettuna      NUMERIC;
  sakot_laskutetaan_ind_korotus          NUMERIC;
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

  muutostyot_laskutettu                  NUMERIC;
  muutostyot_laskutettu_ind_korotettuna  NUMERIC;
  muutostyot_laskutettu_ind_korotus      NUMERIC;
  muutostyot_rivi                        RECORD;

  muutostyot_laskutetaan                  NUMERIC;
  muutostyot_laskutetaan_ind_korotettuna  NUMERIC;
  muutostyot_laskutetaan_ind_korotus      NUMERIC;
  mhti RECORD;
  mhti_aikavalilla RECORD;

  akilliset_hoitotyot_laskutettu                  NUMERIC;
  akilliset_hoitotyot_laskutettu_ind_korotettuna  NUMERIC;
  akilliset_hoitotyot_laskutettu_ind_korotus      NUMERIC;

  akilliset_hoitotyot_laskutetaan                  NUMERIC;
  akilliset_hoitotyot_laskutetaan_ind_korotettuna  NUMERIC;
  akilliset_hoitotyot_laskutetaan_ind_korotus      NUMERIC;

  vahinkojen_korjaukset_laskutettu                  NUMERIC;
  vahinkojen_korjaukset_laskutettu_ind_korotettuna  NUMERIC;
  vahinkojen_korjaukset_laskutettu_ind_korotus      NUMERIC;

  vahinkojen_korjaukset_laskutetaan                  NUMERIC;
  vahinkojen_korjaukset_laskutetaan_ind_korotettuna  NUMERIC;
  vahinkojen_korjaukset_laskutetaan_ind_korotus      NUMERIC;

  erilliskustannukset_laskutettu                  NUMERIC;
  erilliskustannukset_laskutettu_ind_korotettuna  NUMERIC;
  erilliskustannukset_laskutettu_ind_korotus      NUMERIC;
  erilliskustannukset_rivi                  RECORD;
  eki RECORD;
  erilliskustannukset_laskutetaan                  NUMERIC;
  erilliskustannukset_laskutetaan_ind_korotettuna  NUMERIC;
  erilliskustannukset_laskutetaan_ind_korotus      NUMERIC;

  bonukset_laskutettu NUMERIC;
  bonukset_laskutettu_ind_korotettuna NUMERIC;
  bonukset_laskutettu_ind_korotus NUMERIC;
  bonukset_rivi RECORD;
  bi RECORD;
  bonukset_laskutetaan NUMERIC;
  bonukset_laskutetaan_ind_korotettuna NUMERIC;
  bonukset_laskutetaan_ind_korotus NUMERIC;

  suolasakko_kaytossa BOOLEAN;
  lampotilat RECORD;
  lampotila_puuttuu BOOLEAN;

  cache laskutusyhteenveto_rivi[];
  rivi laskutusyhteenveto_rivi;
BEGIN

-- Katsotaan löytyykö laskutusyhteenveto jo cachesta
  SELECT
    INTO cache rivit
  FROM laskutusyhteenveto_cache c
  WHERE c.urakka = ur
        AND c.alkupvm = aikavali_alkupvm
        AND c.loppupvm = aikavali_loppupvm;

  IF cache IS NOT NULL THEN
    RAISE NOTICE 'Käytetään muistettua laskutusyhteenvetoa urakalle % aikavälillä % - %', ur, aikavali_alkupvm, aikavali_loppupvm;
    FOREACH rivi IN ARRAY cache
    LOOP
      RETURN NEXT rivi;
    END LOOP;
    RETURN;
  END IF;

  cache := ARRAY[]::laskutusyhteenveto_rivi[];

  perusluku := indeksilaskennan_perusluku(ur);
  SELECT indeksi FROM urakka WHERE id = ur INTO ind;

  -- Loopataan urakan toimenpideinstanssien läpi
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
    RAISE NOTICE '***** Laskutusyhteenvedon laskenta alkaa toimenpiteelle: % *****', t.nimi;
    kht_laskutettu := 0.0;
    kht_laskutettu_ind_korotettuna := 0.0;
    kht_laskutettu_ind_korotus := 0.0;

    -- Hoitokaudella ennen aikaväliä laskutetut kokonaishintaisten töiden kustannukset, myös indeksitarkistuksen kanssa
    FOR khti IN SELECT
                  (SELECT korotus
                   FROM laske_kuukauden_indeksikorotus(kht.vuosi, kht.kuukausi, ind,
                                                       kht.summa, perusluku)) AS ind,
                  (SELECT korotettuna
                   FROM laske_kuukauden_indeksikorotus(kht.vuosi, kht.kuukausi, ind,
                                                       kht.summa, perusluku)) AS kor,
                  kht.summa                                        AS kht_summa
                FROM kokonaishintainen_tyo kht
                WHERE toimenpideinstanssi = t.tpi
                      AND kht.summa IS NOT NULL
                      AND maksupvm >= hk_alkupvm
                      AND maksupvm <= hk_loppupvm
                      AND maksupvm < aikavali_alkupvm
    LOOP
      kht_laskutettu := kht_laskutettu + COALESCE(khti.kht_summa, 0.0);
      kht_laskutettu_ind_korotettuna := kht_laskutettu_ind_korotettuna + khti.kor;
      kht_laskutettu_ind_korotus := kht_laskutettu_ind_korotus + khti.ind;
    END LOOP;

    -- Kokonaishintaiset aikavälillä indeksikorotuksen kanssa
    kht_laskutetaan := 0.0;
    kht_laskutetaan_ind_korotettuna := 0.0;
    kht_laskutetaan_ind_korotus := 0.0;

    FOR khti_laskutetaan IN SELECT
                              (SELECT korotus
                               FROM laske_kuukauden_indeksikorotus(kht.vuosi, kht.kuukausi, ind,
                                                                   kht.summa, perusluku)) AS ind,
                              (SELECT korotettuna
                               FROM laske_kuukauden_indeksikorotus(kht.vuosi, kht.kuukausi, ind,
                                                                   kht.summa, perusluku)) AS kor,
                              kht.summa                                        AS kht_summa
                            FROM kokonaishintainen_tyo kht
                            WHERE toimenpideinstanssi = t.tpi
                                  AND kht.summa IS NOT NULL
                                  AND maksupvm >= hk_alkupvm
                                  AND maksupvm <= hk_loppupvm
                                  AND maksupvm >= aikavali_alkupvm
                                  AND maksupvm <= aikavali_loppupvm
    LOOP
      kht_laskutetaan := kht_laskutetaan + COALESCE(khti_laskutetaan.kht_summa, 0.0);
      kht_laskutetaan_ind_korotettuna := kht_laskutetaan_ind_korotettuna + khti_laskutetaan.kor;
      kht_laskutetaan_ind_korotus := kht_laskutetaan_ind_korotus + khti_laskutetaan.ind;
    END LOOP;

    -- Hoitokaudella tehtyjen yksikköhintaisten töiden kustannukset, jotka lisätään
    -- joko jo laskutettuihin tai nyt laskutettaviin
    yht_laskutettu := 0.0;
    yht_laskutettu_ind_korotettuna := 0.0;
    yht_laskutettu_ind_korotus := 0.0;
    yht_laskutetaan := 0.0;
    yht_laskutetaan_ind_korotettuna := 0.0;
    yht_laskutetaan_ind_korotus := 0.0;

    FOR yhti IN SELECT tt.maara * yht.yksikkohinta AS yht_summa,
                       tot.alkanut AS tot_alkanut,
                       tot.id,
                       tt.toimenpidekoodi,
		       tt.indeksi
                  FROM toteuma_tehtava tt
                  JOIN toteuma tot
		       ON (tt.toteuma = tot.id AND
		           tot.tyyppi = 'yksikkohintainen'::toteumatyyppi AND
		           tot.poistettu IS NOT TRUE)
                  JOIN toimenpidekoodi tpk4 ON tt.toimenpidekoodi = tpk4.id
                  JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
                  JOIN yksikkohintainen_tyo yht
		       ON (tt.toimenpidekoodi = yht.tehtava AND
		           yht.alkupvm <= tot.alkanut::DATE AND yht.loppupvm >= tot.alkanut::DATE AND
 			   yht.yksikkohinta IS NOT NULL AND
			   tpk3.id = t.tpk3_id)
                 WHERE yht.urakka = ur AND
		       tt.poistettu IS NOT TRUE AND
		       tot.urakka = ur AND
		       tot.alkanut::DATE >= hk_alkupvm AND tot.alkanut::DATE <= aikavali_loppupvm
    LOOP
      IF yhti.indeksi THEN
        -- Indeksi käytössä, lasketaan korotus
        SELECT *
        FROM laske_kuukauden_indeksikorotus((SELECT EXTRACT(YEAR FROM yhti.tot_alkanut) :: INTEGER),
                                            (SELECT EXTRACT(MONTH FROM yhti.tot_alkanut) :: INTEGER),
                                            ind, yhti.yht_summa, perusluku)
        INTO yht_rivi;
      ELSE
        -- Indeksi ei käytössä, annetaan summa sellaisenaan
        SELECT yhti.yht_summa AS summa,
	       yhti.yht_summa AS korotettuna,
	       0::NUMERIC as korotus
	  INTO yht_rivi;
      END IF;

      RAISE NOTICE 'yht_rivi: %', yht_rivi;
      IF  yhti.tot_alkanut < aikavali_alkupvm THEN
        -- jo laskutettu
        yht_laskutettu :=  yht_laskutettu + COALESCE(yht_rivi.summa, 0.0);
        yht_laskutettu_ind_korotettuna :=  yht_laskutettu_ind_korotettuna + yht_rivi.korotettuna;
        yht_laskutettu_ind_korotus :=  yht_laskutettu_ind_korotus + yht_rivi.korotus;
      ELSE
        -- laskutetaan nyt
        yht_laskutetaan := yht_laskutetaan + COALESCE(yht_rivi.summa, 0.0);
        yht_laskutetaan_ind_korotettuna := yht_laskutetaan_ind_korotettuna + yht_rivi.korotettuna;
        yht_laskutetaan_ind_korotus := yht_laskutetaan_ind_korotus + yht_rivi.korotus;
      END IF;
    END LOOP;

    -- Hoitokaudella ennen aikaväliä laskutetut sanktiot
    sakot_laskutettu := 0.0;
    sakot_laskutettu_ind_korotettuna := 0.0;
    sakot_laskutettu_ind_korotus := 0.0;
    sakot_laskutetaan := 0.0;
    sakot_laskutetaan_ind_korotettuna := 0.0;
    sakot_laskutetaan_ind_korotus := 0.0;

    FOR sanktiorivi IN SELECT -maara AS maara, perintapvm, indeksi, perintapvm
                       FROM sanktio s
                       WHERE s.toimenpideinstanssi = t.tpi AND
                             s.maara IS NOT NULL AND
                             s.perintapvm >= hk_alkupvm AND
                             s.perintapvm <= aikavali_loppupvm AND
                             s.poistettu IS NOT TRUE
    LOOP
      SELECT *
        FROM laske_kuukauden_indeksikorotus((SELECT EXTRACT(YEAR FROM sanktiorivi.perintapvm) :: INTEGER),
                                            (SELECT EXTRACT(MONTH FROM sanktiorivi.perintapvm) :: INTEGER),
                                            sanktiorivi.indeksi, sanktiorivi.maara, perusluku)
        INTO sakot_rivi;
      IF sanktiorivi.perintapvm < aikavali_alkupvm THEN
        sakot_laskutettu := sakot_laskutettu + COALESCE(sakot_rivi.summa, 0.0);
        sakot_laskutettu_ind_korotettuna := sakot_laskutettu_ind_korotettuna + sakot_rivi.korotettuna;
        sakot_laskutettu_ind_korotus := sakot_laskutettu_ind_korotus + sakot_rivi.korotus;
      ELSE
        sakot_laskutetaan := sakot_laskutetaan + COALESCE(sakot_rivi.summa, 0.0);
        sakot_laskutetaan_ind_korotettuna := sakot_laskutetaan_ind_korotettuna + sakot_rivi.korotettuna;
        sakot_laskutetaan_ind_korotus := sakot_laskutetaan_ind_korotus + sakot_rivi.korotus;
      END IF;
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
                                             hoitokauden_laskettu_suolasakon_maara, ur)
      INTO hoitokauden_laskettu_suolasakko_rivi;

      -- Jos suolasakko ei ole käytössä, ei edetä
      IF (hoitokauden_suolasakko_rivi.hoitokauden_alkuvuosi IS NULL AND
          hoitokauden_suolasakko_rivi.indeksi IS NULL AND
          hoitokauden_suolasakko_rivi.maksukuukausi IS NULL)
      THEN
        RAISE NOTICE 'Suolasakko ei käytössä annetulla aikavälillä urakassa %, aikavali_alkupvm: %, hoitokauden_suolasakko_rivi: %', ur, aikavali_alkupvm, hoitokauden_suolasakko_rivi;
      -- Suolasakko voi olla laskutettu jo hoitokaudella vain kk:ina 6-9 koska mahdolliset laskutus-kk:t ovat 5-9
      ELSIF (hoitokauden_suolasakko_rivi.maksukuukausi < (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER)
             AND (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER) < 10)
        THEN
          RAISE NOTICE 'Suolasakko on laskutettu aiemmin hoitokaudella kuukautena %', hoitokauden_suolasakko_rivi.maksukuukausi;
          suolasakot_laskutettu := hoitokauden_laskettu_suolasakko_rivi.summa;
        suolasakot_laskutettu_ind_korotettuna := hoitokauden_laskettu_suolasakko_rivi.korotettuna;
        suolasakot_laskutettu_ind_korotus := hoitokauden_laskettu_suolasakko_rivi.korotus;
        -- Jos valittu yksittäinen kuukausi on maksukuukausi TAI jos kyseessä koko hoitokauden raportti (poikkeustapaus)
      ELSIF (hoitokauden_suolasakko_rivi.maksukuukausi = (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER))
            OR (aikavali_alkupvm = hk_alkupvm AND aikavali_loppupvm = hk_loppupvm) THEN
        RAISE NOTICE 'Suolasakko laskutetaan tässä kuussa % tai kyseessä koko hoitokauden LYV-raportti.', hoitokauden_suolasakko_rivi.maksukuukausi;
        suolasakot_laskutetaan := hoitokauden_laskettu_suolasakko_rivi.summa;
        suolasakot_laskutetaan_ind_korotettuna := hoitokauden_laskettu_suolasakko_rivi.korotettuna;
        suolasakot_laskutetaan_ind_korotus := hoitokauden_laskettu_suolasakko_rivi.korotus;
      ELSE
        RAISE NOTICE 'Suolasakkoa ei vielä laskutettu, maksukuukauden arvo: %', hoitokauden_suolasakko_rivi.maksukuukausi;
      END IF;
    END IF;


    -- Muutos- ja lisätyöt hoitokaudella
    muutostyot_laskutettu := 0.0;
    muutostyot_laskutettu_ind_korotettuna := 0.0;
    muutostyot_laskutettu_ind_korotus := 0.0;
    muutostyot_laskutetaan := 0.0;
    muutostyot_laskutetaan_ind_korotettuna := 0.0;
    muutostyot_laskutetaan_ind_korotus := 0.0;
    akilliset_hoitotyot_laskutettu := 0.0;
    akilliset_hoitotyot_laskutettu_ind_korotettuna := 0.0;
    akilliset_hoitotyot_laskutettu_ind_korotus := 0.0;
    akilliset_hoitotyot_laskutetaan := 0.0;
    akilliset_hoitotyot_laskutetaan_ind_korotettuna := 0.0;
    akilliset_hoitotyot_laskutetaan_ind_korotus := 0.0;
    vahinkojen_korjaukset_laskutettu := 0.0;
    vahinkojen_korjaukset_laskutettu_ind_korotettuna := 0.0;
    vahinkojen_korjaukset_laskutettu_ind_korotus := 0.0;
    vahinkojen_korjaukset_laskutetaan := 0.0;
    vahinkojen_korjaukset_laskutetaan_ind_korotettuna := 0.0;
    vahinkojen_korjaukset_laskutetaan_ind_korotus := 0.0;

    FOR mhti IN SELECT COALESCE(tt.paivan_hinta, tt.maara * mht.yksikkohinta) AS mht_summa,
                       tot.alkanut AS tot_alkanut,
		       tt.indeksi,
		       tot.tyyppi
                  FROM toteuma_tehtava tt
                  JOIN toteuma tot
		       ON (tt.toteuma = tot.id AND
		           tot.tyyppi IN ('muutostyo', 'lisatyo',
			                  'vahinkojen-korjaukset','akillinen-hoitotyo') AND
			   tot.poistettu IS NOT TRUE)
                  JOIN toimenpidekoodi tpk4 ON tt.toimenpidekoodi = tpk4.id
                  JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
                  LEFT JOIN muutoshintainen_tyo mht
		       ON (tt.toimenpidekoodi = mht.tehtava AND
		           mht.urakka = tot.urakka AND
               mht.sopimus = tot.sopimus AND mht.poistettu IS NOT TRUE)
           WHERE tot.urakka = ur AND
		       tpk3.id = t.tpk3_id AND
		       tot.alkanut::DATE >= hk_alkupvm AND tot.alkanut::DATE <= aikavali_loppupvm
    LOOP
      IF mhti.indeksi = TRUE THEN
        SELECT *
          FROM laske_kuukauden_indeksikorotus((SELECT EXTRACT(YEAR FROM mhti.tot_alkanut) :: INTEGER),
                                              (SELECT EXTRACT(MONTH FROM mhti.tot_alkanut) :: INTEGER),
                                              ind, mhti.mht_summa, perusluku)
          INTO muutostyot_rivi;
      ELSE
        SELECT mhti.mht_summa AS summa, mhti.mht_summa AS korotettuna, 0::NUMERIC as korotus
	  INTO muutostyot_rivi;
      END IF;
      IF mhti.tyyppi = 'akillinen-hoitotyo' THEN
        IF mhti.tot_alkanut < aikavali_alkupvm THEN
	  akilliset_hoitotyot_laskutettu := akilliset_hoitotyot_laskutettu + COALESCE(muutostyot_rivi.summa, 0.0);
	  akilliset_hoitotyot_laskutettu_ind_korotettuna := akilliset_hoitotyot_laskutettu_ind_korotettuna + muutostyot_rivi.korotettuna;
	  akilliset_hoitotyot_laskutettu_ind_korotus := akilliset_hoitotyot_laskutettu_ind_korotus + muutostyot_rivi.korotus;
	ELSE
	  akilliset_hoitotyot_laskutetaan := akilliset_hoitotyot_laskutetaan + COALESCE(muutostyot_rivi.summa, 0.0);
	  akilliset_hoitotyot_laskutetaan_ind_korotettuna := akilliset_hoitotyot_laskutetaan_ind_korotettuna + muutostyot_rivi.korotettuna;
	  akilliset_hoitotyot_laskutetaan_ind_korotus := akilliset_hoitotyot_laskutetaan_ind_korotus + muutostyot_rivi.korotus;
	END IF;
      ELSIF mhti.tyyppi = 'vahinkojen-korjaukset' THEN
        IF mhti.tot_alkanut < aikavali_alkupvm THEN
          vahinkojen_korjaukset_laskutettu := vahinkojen_korjaukset_laskutettu + COALESCE(muutostyot_rivi.summa, 0.0);
          vahinkojen_korjaukset_laskutettu_ind_korotettuna := vahinkojen_korjaukset_laskutettu_ind_korotettuna + muutostyot_rivi.korotettuna;
          vahinkojen_korjaukset_laskutettu_ind_korotus := vahinkojen_korjaukset_laskutettu_ind_korotus + muutostyot_rivi.korotus;
        ELSE
          vahinkojen_korjaukset_laskutetaan := vahinkojen_korjaukset_laskutetaan + COALESCE(muutostyot_rivi.summa, 0.0);
          vahinkojen_korjaukset_laskutetaan_ind_korotettuna := vahinkojen_korjaukset_laskutetaan_ind_korotettuna + muutostyot_rivi.korotettuna;
          vahinkojen_korjaukset_laskutetaan_ind_korotus := vahinkojen_korjaukset_laskutetaan_ind_korotus + muutostyot_rivi.korotus;
        END IF;
      ELSE
        RAISE NOTICE 'mht tyyppiä %, alkanut %, summa %', mhti.tyyppi, mhti.tot_alkanut, muutostyot_rivi.summa;
        IF mhti.tot_alkanut < aikavali_alkupvm THEN
          muutostyot_laskutettu :=  muutostyot_laskutettu + COALESCE(muutostyot_rivi.summa, 0.0);
          muutostyot_laskutettu_ind_korotettuna :=  muutostyot_laskutettu_ind_korotettuna + muutostyot_rivi.korotettuna;
          muutostyot_laskutettu_ind_korotus :=  muutostyot_laskutettu_ind_korotus + muutostyot_rivi.korotus;
        ELSE
          muutostyot_laskutetaan := muutostyot_laskutetaan + COALESCE(muutostyot_rivi.summa, 0.0);
          muutostyot_laskutetaan_ind_korotettuna := muutostyot_laskutetaan_ind_korotettuna + muutostyot_rivi.korotettuna;
          muutostyot_laskutetaan_ind_korotus := muutostyot_laskutetaan_ind_korotus + muutostyot_rivi.korotus;
        END IF;
      END IF;
    END LOOP;
    RAISE NOTICE 'Äkilliset hoitotyot laskutettu / laskutetaan: % / %', akilliset_hoitotyot_laskutettu, akilliset_hoitotyot_laskutetaan;
    RAISE NOTICE 'Vahinkojen korjaukset laskutettu / laskutetaan: % / %', vahinkojen_korjaukset_laskutettu, vahinkojen_korjaukset_laskutetaan;
    RAISE NOTICE 'Muutostyöt laskutettu / laskutetaan: % / %', muutostyot_laskutettu, muutostyot_laskutetaan;

    -- ERILLISKUSTANNUKSET  hoitokaudella
    -- bonukset lasketaan erikseen tyypin perusteella
    erilliskustannukset_laskutettu := 0.0;
    erilliskustannukset_laskutettu_ind_korotettuna := 0.0;
    erilliskustannukset_laskutettu_ind_korotus := 0.0;
    erilliskustannukset_laskutetaan := 0.0;
    erilliskustannukset_laskutetaan_ind_korotettuna := 0.0;
    erilliskustannukset_laskutetaan_ind_korotus := 0.0;
    bonukset_laskutettu := 0.0;
    bonukset_laskutettu_ind_korotettuna := 0.0;
    bonukset_laskutettu_ind_korotus := 0.0;
    bonukset_laskutetaan := 0.0;
    bonukset_laskutetaan_ind_korotettuna := 0.0;
    bonukset_laskutetaan_ind_korotus := 0.0;

    FOR eki IN
        SELECT ek.pvm, ek.rahasumma, ek.indeksin_nimi, ek.tyyppi
          FROM erilliskustannus ek
         WHERE ek.sopimus IN (SELECT id FROM sopimus WHERE urakka = ur) AND
	       ek.toimenpideinstanssi = t.tpi AND
	       ek.pvm >= hk_alkupvm AND ek.pvm <= aikavali_loppupvm AND
	       ek.poistettu IS NOT TRUE
    LOOP
      IF eki.tyyppi = 'asiakastyytyvaisyysbonus' THEN
        -- Bonus
	SELECT *
          FROM laske_hoitokauden_asiakastyytyvaisyysbonus(ur, eki.pvm, ind, eki.rahasumma)
          INTO bonukset_rivi;
        IF eki.pvm < aikavali_alkupvm THEN
          bonukset_laskutettu :=  bonukset_laskutettu + COALESCE(bonukset_rivi.summa, 0.0);
          bonukset_laskutettu_ind_korotettuna :=  bonukset_laskutettu_ind_korotettuna + bonukset_rivi.korotettuna;
          bonukset_laskutettu_ind_korotus :=  bonukset_laskutettu_ind_korotus + bonukset_rivi.korotus;
        ELSE
          bonukset_laskutetaan :=  bonukset_laskutetaan + COALESCE(bonukset_rivi.summa, 0.0);
          bonukset_laskutetaan_ind_korotettuna :=  bonukset_laskutetaan_ind_korotettuna + bonukset_rivi.korotettuna;
          bonukset_laskutetaan_ind_korotus :=  bonukset_laskutetaan_ind_korotus + bonukset_rivi.korotus;
        END IF;
      ELSE
        -- Muu erilliskustannus kuin bonus
        SELECT *
          FROM laske_kuukauden_indeksikorotus((SELECT EXTRACT(YEAR FROM eki.pvm) :: INTEGER),
                                              (SELECT EXTRACT(MONTH FROM eki.pvm) :: INTEGER),
                                              eki.indeksin_nimi, eki.rahasumma, perusluku)
          INTO erilliskustannukset_rivi;
        IF eki.pvm < aikavali_alkupvm THEN
          erilliskustannukset_laskutettu :=  erilliskustannukset_laskutettu + COALESCE(erilliskustannukset_rivi.summa, 0.0);
          erilliskustannukset_laskutettu_ind_korotettuna :=  erilliskustannukset_laskutettu_ind_korotettuna + erilliskustannukset_rivi.korotettuna;
          erilliskustannukset_laskutettu_ind_korotus :=  erilliskustannukset_laskutettu_ind_korotus + erilliskustannukset_rivi.korotus;
        ELSE
          erilliskustannukset_laskutetaan :=  erilliskustannukset_laskutetaan + COALESCE(erilliskustannukset_rivi.summa, 0.0);
          erilliskustannukset_laskutetaan_ind_korotettuna :=  erilliskustannukset_laskutetaan_ind_korotettuna + erilliskustannukset_rivi.korotettuna;
          erilliskustannukset_laskutetaan_ind_korotus :=  erilliskustannukset_laskutetaan_ind_korotus + erilliskustannukset_rivi.korotus;
        END IF;
      END IF;
    END LOOP;
    RAISE NOTICE 'Erilliskustannuksia laskutettu / laskutetaan: % / %', erilliskustannukset_laskutettu, erilliskustannukset_laskutetaan;
    RAISE NOTICE 'Bonuksia laskutettu / laskutetaan: % / %', bonukset_laskutettu, bonukset_laskutetaan;

    -- Onko suolasakko käytössä urakassa
    IF (select count(*) FROM suolasakko WHERE urakka = ur
                                              AND kaytossa
                                              AND hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM hk_alkupvm) :: INTEGER)) > 0
    THEN suolasakko_kaytossa = TRUE;
    ELSE suolasakko_kaytossa = FALSE;
    END IF;

    -- Ovatko suolasakon tarvitsemat lämpötilat kannassa
    SELECT * INTO lampotilat FROM lampotilat
    WHERE urakka = ur AND alkupvm = hk_alkupvm AND loppupvm = hk_loppupvm;

    IF (lampotilat IS NULL OR lampotilat.keskilampotila IS NULL OR lampotilat.pitka_keskilampotila IS NULL)
    THEN
      RAISE NOTICE 'Urakalle % ei ole lämpötiloja hoitokaudelle % - %', ur, hk_alkupvm, hk_loppupvm;
      RAISE NOTICE 'Keskilämpötila hoitokaudella %, pitkän ajan keskilämpötila %', lampotilat.keskilampotila, lampotilat.pitka_keskilampotila;
      lampotila_puuttuu = TRUE;
    ELSE
      lampotila_puuttuu = FALSE;
    END IF;

    -- Indeksisummat
    kaikki_paitsi_kht_laskutettu_ind_korotus := 0.0;
    kaikki_laskutettu_ind_korotus := 0.0;
    kaikki_paitsi_kht_laskutetaan_ind_korotus := 0.0;
    kaikki_laskutetaan_ind_korotus := 0.0;

    kaikki_paitsi_kht_laskutettu_ind_korotus := yht_laskutettu_ind_korotus + sakot_laskutettu_ind_korotus + COALESCE(suolasakot_laskutettu_ind_korotus, 0.0) +
                                                muutostyot_laskutettu_ind_korotus + akilliset_hoitotyot_laskutettu_ind_korotus +
                                                vahinkojen_korjaukset_laskutettu_ind_korotus + erilliskustannukset_laskutettu_ind_korotus + bonukset_laskutettu_ind_korotus;
    kaikki_laskutettu_ind_korotus := kaikki_paitsi_kht_laskutettu_ind_korotus + kht_laskutettu_ind_korotus;

    kaikki_paitsi_kht_laskutetaan_ind_korotus := yht_laskutetaan_ind_korotus + sakot_laskutetaan_ind_korotus + COALESCE(suolasakot_laskutetaan_ind_korotus, 0.0) +
                                                 muutostyot_laskutetaan_ind_korotus + akilliset_hoitotyot_laskutetaan_ind_korotus  +
                                                 vahinkojen_korjaukset_laskutetaan_ind_korotus + erilliskustannukset_laskutetaan_ind_korotus + bonukset_laskutetaan_ind_korotus;
    kaikki_laskutetaan_ind_korotus := kaikki_paitsi_kht_laskutetaan_ind_korotus + kht_laskutetaan_ind_korotus;


    -- Kustannusten kokonaissummat
    kaikki_paitsi_kht_laskutettu := 0.0;
    kaikki_laskutettu := 0.0;

    kaikki_paitsi_kht_laskutetaan := 0.0;
    kaikki_laskutetaan := 0.0;

    kaikki_paitsi_kht_laskutettu := yht_laskutettu_ind_korotettuna + sakot_laskutettu_ind_korotettuna +
                                    COALESCE(suolasakot_laskutettu_ind_korotettuna, 0.0) + muutostyot_laskutettu_ind_korotettuna +
                                    akilliset_hoitotyot_laskutettu_ind_korotettuna +
                                    vahinkojen_korjaukset_laskutettu_ind_korotettuna + erilliskustannukset_laskutettu_ind_korotettuna +
                                    bonukset_laskutettu_ind_korotettuna
                                    --Aurasta: myös kok.hint. töiden indeksitarkistus laskettava tähän mukaan
                                    + kht_laskutettu_ind_korotus;

    kaikki_laskutettu := kaikki_paitsi_kht_laskutettu + kht_laskutettu;

    kaikki_paitsi_kht_laskutetaan := yht_laskutetaan_ind_korotettuna + sakot_laskutetaan_ind_korotettuna +
                                     COALESCE(suolasakot_laskutetaan_ind_korotettuna, 0.0) + muutostyot_laskutetaan_ind_korotettuna +
                                     akilliset_hoitotyot_laskutetaan_ind_korotettuna +
                                     vahinkojen_korjaukset_laskutetaan_ind_korotettuna + erilliskustannukset_laskutetaan_ind_korotettuna +
                                     bonukset_laskutetaan_ind_korotettuna
                                     --Aurasta: myös kok.hint. töiden indeksitarkistus laskettava tähän mukaan
                                     + kht_laskutetaan_ind_korotus;
    kaikki_laskutetaan := kaikki_paitsi_kht_laskutetaan + kht_laskutetaan;


    RAISE NOTICE '
    Yhteenveto:';
    RAISE NOTICE 'LASKUTETTU ENNEN AIKAVÄLIÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
    RAISE NOTICE 'kht_laskutettu: %', kht_laskutettu;
    RAISE NOTICE 'kht_laskutettu_ind_korotettuna: %', kht_laskutettu_ind_korotettuna;
    RAISE NOTICE 'yht_laskutettu: %', yht_laskutettu;
    RAISE NOTICE 'yht_laskutettu_ind_korotettuna: %', yht_laskutettu_ind_korotettuna;
    RAISE NOTICE 'sakot_laskutettu: %', sakot_laskutettu;
    RAISE NOTICE 'sakot_laskutettu_ind_korotettuna: %', sakot_laskutettu_ind_korotettuna;
    RAISE NOTICE 'suolasakot_laskutettu: %', suolasakot_laskutettu;
    RAISE NOTICE 'suolasakot_laskutettu_ind_korotettuna: %', suolasakot_laskutettu_ind_korotettuna;
    RAISE NOTICE 'muutostyot_laskutettu: %', muutostyot_laskutettu;
    RAISE NOTICE 'muutostyot_laskutettu_ind_korotettuna: %', muutostyot_laskutettu_ind_korotettuna;
    RAISE NOTICE 'akilliset_hoitotyot_laskutettu: %', akilliset_hoitotyot_laskutettu;
    RAISE NOTICE 'akilliset_hoitotyot_laskutettu_ind_korotettuna: %', akilliset_hoitotyot_laskutettu_ind_korotettuna;
    RAISE NOTICE 'vahinkojen_korjaukset_laskutettu: %', vahinkojen_korjaukset_laskutettu;
    RAISE NOTICE 'vahinkojen_korjaukset_laskutettu_ind_korotettuna: %', vahinkojen_korjaukset_laskutettu_ind_korotettuna;
    RAISE NOTICE 'erilliskustannukset_laskutettu: %', erilliskustannukset_laskutettu;
    RAISE NOTICE 'erilliskustannukset_laskutettu_ind_korotettuna: %', erilliskustannukset_laskutettu_ind_korotettuna;
    RAISE NOTICE 'bonukset_laskutettu: %', bonukset_laskutettu;
    RAISE NOTICE 'bonukset_laskutettu_ind_korotettuna: %', bonukset_laskutettu_ind_korotettuna;

    RAISE NOTICE '
    LASKUTETAAN AIKAVÄLILLÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
    RAISE NOTICE 'kht_laskutetaan: %', kht_laskutetaan;
    RAISE NOTICE 'kht_laskutetaan_ind_korotettuna: %', kht_laskutetaan_ind_korotettuna;
    RAISE NOTICE 'yht_laskutetaan: %', yht_laskutetaan;
    RAISE NOTICE 'yht_laskutetaan_ind_korotettuna: %', yht_laskutetaan_ind_korotettuna;
    RAISE NOTICE 'sakot_laskutetaan: %', sakot_laskutetaan;
    RAISE NOTICE 'sakot_laskutetaan_ind_korotettuna: %', sakot_laskutetaan_ind_korotettuna;
    RAISE NOTICE 'suolasakot_laskutetaan: %', suolasakot_laskutetaan;
    RAISE NOTICE 'suolasakot_laskutetaan_ind_korotettuna: %', suolasakot_laskutetaan_ind_korotettuna;
    RAISE NOTICE 'muutostyot_laskutetaan: %', muutostyot_laskutetaan;
    RAISE NOTICE 'muutostyot_laskutetaan_ind_korotettuna: %', muutostyot_laskutetaan_ind_korotettuna;
    RAISE NOTICE 'akilliset_hoitotyot_laskutetaan: %', akilliset_hoitotyot_laskutetaan;
    RAISE NOTICE 'akilliset_hoitotyot_laskutetaan_ind_korotettuna: %', akilliset_hoitotyot_laskutetaan_ind_korotettuna;
    RAISE NOTICE 'vahinkojen_korjaukset_laskutetaan: %', vahinkojen_korjaukset_laskutetaan;
    RAISE NOTICE 'vahinkojen_korjaukset_laskutetaan_ind_korotettuna: %', vahinkojen_korjaukset_laskutetaan_ind_korotettuna;
    RAISE NOTICE 'erilliskustannukset_laskutetaan: %', erilliskustannukset_laskutetaan;
    RAISE NOTICE 'erilliskustannukset_laskutetaan_ind_korotettuna: %', erilliskustannukset_laskutetaan_ind_korotettuna;
    RAISE NOTICE 'bonukset_laskutetaan: %', bonukset_laskutetaan;
    RAISE NOTICE 'bonukset_laskutetaan_ind_korotettuna: %', bonukset_laskutetaan_ind_korotettuna;

    RAISE NOTICE 'kaikki_paitsi_kht_laskutettu_ind_korotus: %', kaikki_paitsi_kht_laskutettu_ind_korotus;
    RAISE NOTICE 'kaikki_laskutettu_ind_korotus: %', kaikki_laskutettu_ind_korotus;
    RAISE NOTICE 'kaikki_paitsi_kht_laskutetaan_ind_korotus: %', kaikki_paitsi_kht_laskutetaan_ind_korotus;
    RAISE NOTICE 'kaikki_laskutetaan_ind_korotus: %', kaikki_laskutetaan_ind_korotus;
    RAISE NOTICE 'kaikki_paitsi_kht_laskutettu: %', kaikki_paitsi_kht_laskutettu;
    RAISE NOTICE 'kaikki_laskutettu: %', kaikki_laskutettu;
    RAISE NOTICE 'kaikki_paitsi_kht_laskutetaan: %', kaikki_paitsi_kht_laskutetaan;
    RAISE NOTICE 'kaikki_laskutetaan: %', kaikki_laskutetaan;

    RAISE NOTICE 'suolasakko_kaytossa: %', suolasakko_kaytossa;
    RAISE NOTICE 'lampotila_puuttuu: %', lampotila_puuttuu;
    RAISE NOTICE 'indeksilaskennan perusluku: %', perusluku;

    RAISE NOTICE '***** Käsitelly loppui toimenpiteelle: %  *****

    ', t.nimi;

    rivi := (t.nimi, t.tuotekoodi, t.tpi, perusluku,
             kaikki_paitsi_kht_laskutettu_ind_korotus, kaikki_laskutettu_ind_korotus,
	     kaikki_paitsi_kht_laskutetaan_ind_korotus, kaikki_laskutetaan_ind_korotus,
	     kaikki_paitsi_kht_laskutettu, kaikki_laskutettu,
             kaikki_paitsi_kht_laskutetaan, kaikki_laskutetaan,
             kht_laskutettu, kht_laskutettu_ind_korotettuna, kht_laskutettu_ind_korotus,
 	     kht_laskutetaan, kht_laskutetaan_ind_korotettuna, kht_laskutetaan_ind_korotus,
 	     yht_laskutettu, yht_laskutettu_ind_korotettuna, yht_laskutettu_ind_korotus,
 	     yht_laskutetaan, yht_laskutetaan_ind_korotettuna, yht_laskutetaan_ind_korotus,
 	     sakot_laskutettu, sakot_laskutettu_ind_korotettuna, sakot_laskutettu_ind_korotus,
 	     sakot_laskutetaan, sakot_laskutetaan_ind_korotettuna, sakot_laskutetaan_ind_korotus,
 	     suolasakot_laskutettu, suolasakot_laskutettu_ind_korotettuna, suolasakot_laskutettu_ind_korotus,
 	     suolasakot_laskutetaan, suolasakot_laskutetaan_ind_korotettuna, suolasakot_laskutetaan_ind_korotus,
 	     muutostyot_laskutettu, muutostyot_laskutettu_ind_korotettuna, muutostyot_laskutettu_ind_korotus,
 	     muutostyot_laskutetaan, muutostyot_laskutetaan_ind_korotettuna, muutostyot_laskutetaan_ind_korotus,
 	     akilliset_hoitotyot_laskutettu, akilliset_hoitotyot_laskutettu_ind_korotettuna, akilliset_hoitotyot_laskutettu_ind_korotus,
 	     akilliset_hoitotyot_laskutetaan, akilliset_hoitotyot_laskutetaan_ind_korotettuna, akilliset_hoitotyot_laskutetaan_ind_korotus,
 	     erilliskustannukset_laskutettu, erilliskustannukset_laskutettu_ind_korotettuna, erilliskustannukset_laskutettu_ind_korotus,
 	     erilliskustannukset_laskutetaan, erilliskustannukset_laskutetaan_ind_korotettuna, erilliskustannukset_laskutetaan_ind_korotus,
 	     bonukset_laskutettu, bonukset_laskutettu_ind_korotettuna, bonukset_laskutettu_ind_korotus,
 	     bonukset_laskutetaan, bonukset_laskutetaan_ind_korotettuna, bonukset_laskutetaan_ind_korotus,
 	     suolasakko_kaytossa, lampotila_puuttuu,
       vahinkojen_korjaukset_laskutettu, vahinkojen_korjaukset_laskutettu_ind_korotettuna, vahinkojen_korjaukset_laskutettu_ind_korotus,
       vahinkojen_korjaukset_laskutetaan, vahinkojen_korjaukset_laskutetaan_ind_korotettuna, vahinkojen_korjaukset_laskutetaan_ind_korotus

    );

    cache := cache || rivi;
    RETURN NEXT rivi;

  END LOOP;

  RAISE NOTICE 'tallennetaan cacheen';
  -- Tallenna cacheen ajettu laskutusyhteenveto
  -- Jos indeksit tai urakan toteumat muuttuvat, pitää niiden transaktioiden
  -- poistaa myös cache
  INSERT
    INTO laskutusyhteenveto_cache (urakka, alkupvm, loppupvm, rivit)
  VALUES (ur, aikavali_alkupvm, aikavali_loppupvm, cache)
      ON CONFLICT ON CONSTRAINT uniikki_urakka_aika
      DO UPDATE SET rivit = cache, tallennettu = NOW();
END;
$$ LANGUAGE plpgsql;