-- Palauttaa maksuerien kokonaissummat
  -- Tallentaa laskutusyhteenvetoon päivittyneen tilanteen
CREATE OR REPLACE FUNCTION laskutusyhteenveto_teiden_hoito(hk_alkupvm date, hk_loppupvm date, aikavali_alkupvm date,
                                                           aikavali_loppupvm date,
                                                           ur integer) returns SETOF laskutusyhteenveto_rivi
  language plpgsql
as
$$
DECLARE
  t                                         RECORD;
  perusluku                                 NUMERIC; -- urakan indeksilaskennan perusluku (urakkasopimusta edeltävän vuoden joulukuusta 3kk ka)

  kaikki_paitsi_kht_laskutettu              NUMERIC;
  kaikki_laskutettu                         NUMERIC;
  kaikki_paitsi_kht_laskutetaan             NUMERIC;
  kaikki_laskutetaan                        NUMERIC;
  kit_laskutettu                            NUMERIC;
  kit_laskutetaan                           NUMERIC;
  kiti                                      RECORD;
  kit_rivi                                  RECORD;
  kat_laskutettu                            NUMERIC;
  kat_laskutetaan                           NUMERIC;
  kati                                      RECORD;
  kat_rivi                                  RECORD;
  yht_laskutettu                            NUMERIC;
  yht_laskutetaan                           NUMERIC;
  yhti                                      RECORD;
  yht_rivi                                  RECORD;
  kht_laskutettu                            NUMERIC;
  kht_laskutetaan                           NUMERIC;
  khti                                      RECORD;
  kht_rivi                                  RECORD;
  khti_laskutetaan                          RECORD;

  -- Äkilliset hoitotyöt
  aht_laskutettu                            NUMERIC;
  aht_laskutetaan                           NUMERIC;
  ahti                                      RECORD;
  aht_rivi                                  RECORD;
  ahti_laskutetaan                          RECORD;

  -- Vahinkojen korjaukset
  mt_laskutettu                             NUMERIC;
  mt_laskutetaan                            NUMERIC;
  mti                                       RECORD;
  mt_rivi                                   RECORD;
  sakot_laskutettu                          NUMERIC;
  sakot_rivi                                RECORD;
  sakot_laskutetaan                         NUMERIC;
  sanktiorivi                               RECORD;
  suolasakot_laskutettu                     NUMERIC;
  suolasakot_laskutetaan                    NUMERIC;
  hoitokauden_suolasakko_rivi               RECORD;
  hoitokauden_laskettu_suolasakko_rivi      indeksitarkistettu_suolasakko_rivi;
  hoitokauden_laskettu_suolasakon_maara     NUMERIC;
  bonukset_laskutettu                       NUMERIC;
  bonukset_laskutetaan                      NUMERIC;
  suolasakko_kaytossa                       BOOLEAN;
  lampotilat                                RECORD;
  lampotila_puuttuu                         BOOLEAN;
  cache                                     laskutusyhteenveto_rivi[];
  rivi                                      laskutusyhteenveto_rivi;
BEGIN

  -- Katsotaan löytyykö laskutusyhteenveto jo cachesta
  SELECT INTO cache rivit
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

  cache := ARRAY []::laskutusyhteenveto_rivi[];

  perusluku := indeksilaskennan_perusluku(ur);

  -- Teiden hoidon urakoissa (MHU) ei lasketa indeksejä.

  -- Loopataan urakan toimenpideinstanssien läpi
  FOR t IN SELECT tpk2.nimi  AS nimi,
                  tpk2.koodi AS tuotekoodi,
                  tpi.id     AS tpi,
                  tpk3.id    AS tpk3_id
           FROM toimenpideinstanssi tpi
                  JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                  JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id
           WHERE tpi.urakka = ur
    LOOP
      RAISE NOTICE '***** Laskutusyhteenvedon laskenta alkaa toimenpiteelle: % *****', t.nimi;
      kht_laskutettu := 0.0;


      -- Hoitokaudella ennen aikaväliä laskutetut kiinteähintaisten töiden kustannukset
      -- TODO: Tuleeko kiinteähintaisista lasku vai
      -- Hae
      kit_laskutettu := 0.0;
      kit_laskutetaan := 0.0;

      FOR kiti IN
        SELECT summa            as kit_summa,
               lk.suoritus_alku AS tot_alkanut,
               lk.id            AS id,
               lk.tehtava       AS toimenpidekoodi,
               NULL             as indeksi
        FROM lasku_kohdistus lk
               JOIN toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
        WHERE lk.maksueratyyppi = 'lisatyo' -- TODO: Placeholder. Tällaista maksuerätyyppiä ei ole. Kiinteähintaiset lähetetään kokonaishintaisessa maksueraässä.
          AND lk.poistettu IS NOT TRUE
          AND lk.suoritus_alku BETWEEN hk_alkupvm AND aikavali_loppupvm

        LOOP

          -- Indeksi ei käytössä, annetaan summa sellaisenaan
          SELECT kiti.kit_summa AS summa,
                 kiti.kit_summa AS korotettuna,
                 0::NUMERIC     as korotus
                 INTO kit_rivi;

          RAISE NOTICE 'kit_rivi: %', kit_rivi;
          IF kiti.tot_alkanut < aikavali_alkupvm THEN
            -- jo laskutettu
            kit_laskutettu := kit_laskutettu + COALESCE(kit_rivi.summa, 0.0);
          ELSE
            -- laskutetaan nyt
            kit_laskutetaan := kit_laskutetaan + COALESCE(kit_rivi.summa, 0.0);
          END IF;
        END LOOP;

      -- Hoitokaudella ennen aikaväliä laskutetut kustannusarvioitujen töiden kustannukset
      kat_laskutettu := 0.0;
      kat_laskutetaan := 0.0;

      FOR kati IN
        SELECT summa            as kat_summa,
               lk.suoritus_alku AS tot_alkanut,
               lk.id            AS id,
               lk.tehtava       AS toimenpidekoodi,
               NULL             as indeksi
        FROM lasku_kohdistus lk
               JOIN toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
        WHERE lk.maksueratyyppi = 'lisatyo' -- TODO: Placeholder. Tällaista maksuerätyyppiä ei ole. Kustannusarvioidut lähetetään kokonaishintaisessa maksueraässä.
          AND lk.poistettu IS NOT TRUE
          AND lk.suoritus_alku BETWEEN hk_alkupvm AND aikavali_loppupvm

        LOOP

          -- Indeksi ei käytössä, annetaan summa sellaisenaan
          SELECT kati.kat_summa AS summa,
                 kati.kat_summa AS korotettuna,
                 0::NUMERIC     as korotus
                 INTO kat_rivi;

          RAISE NOTICE 'kat_rivi: %', kat_rivi;
          IF kati.tot_alkanut < aikavali_alkupvm THEN
            -- jo laskutettu
            kat_laskutettu := kat_laskutettu + COALESCE(kat_rivi.summa, 0.0);
          ELSE
            -- laskutetaan nyt
            kat_laskutetaan := kat_laskutetaan + COALESCE(kat_rivi.summa, 0.0);
          END IF;
        END LOOP;


      -- Hoitokaudella tehtyjen yksikköhintaisten töiden kustannukset, jotka lisätään
      -- joko jo laskutettuihin tai nyt laskutettaviin
      yht_laskutettu := 0.0;
      yht_laskutetaan := 0.0;

      FOR yhti IN
        SELECT summa            as yht_summa,
               lk.suoritus_alku AS tot_alkanut,
               lk.id            AS id,
               lk.tehtava       AS toimenpidekoodi,
               NULL             as indeksi
        FROM lasku_kohdistus lk
               JOIN toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
        WHERE lk.maksueratyyppi = 'yksikkohintainen' -- TODO: Placeholder. Tällaista maksuerätyyppiä ei ole. Yksikköhintaiset lähetetään kokonaishintaisessa maksueraässä.
          AND lk.poistettu IS NOT TRUE
          AND lk.suoritus_alku BETWEEN hk_alkupvm AND aikavali_loppupvm

        LOOP

          -- Indeksi ei käytössä, annetaan summa sellaisenaan
          SELECT yhti.yht_summa AS summa,
                 yhti.yht_summa AS korotettuna,
                 0::NUMERIC     as korotus
                 INTO yht_rivi;

          RAISE NOTICE 'yht_rivi: %', yht_rivi;
          IF yhti.tot_alkanut < aikavali_alkupvm THEN
            -- jo laskutettu
            yht_laskutettu := yht_laskutettu + COALESCE(yht_rivi.summa, 0.0);
          ELSE
            -- laskutetaan nyt
            yht_laskutetaan := yht_laskutetaan + COALESCE(yht_rivi.summa, 0.0);
          END IF;
        END LOOP;


      -- Hoitokaudella ennen aikaväliä laskutetut kokonaishintaisten töiden kustannukset
      -- Teiden hoidon urakoissa kokonaishintaisiin töihin lasketaan kiinteähintaiset, kustannusarvioidut ja yksikköhintaiset (sic) työt
      kht_laskutettu := 0.0;
      kht_laskutetaan := 0.0;

      FOR khti IN
        SELECT summa            as kht_summa,
               lk.suoritus_alku AS tot_alkanut,
               lk.id            AS id,
               lk.tehtava       AS toimenpidekoodi,
               NULL             as indeksi
        FROM lasku_kohdistus lk
               JOIN toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
        WHERE lk.maksueratyyppi = 'kokonaishintainen' -- TODO: Sisältää kiinteähintaiset, kustannusarvioidut ja yksikkohintaiset työt
          AND lk.poistettu IS NOT TRUE
          AND lk.suoritus_alku BETWEEN hk_alkupvm AND aikavali_loppupvm

        LOOP

          -- Indeksi ei käytössä, annetaan summa sellaisenaan
          SELECT khti.kht_summa AS summa,
                 khti.kht_summa AS korotettuna,
                 0::NUMERIC     as korotus
                 INTO kht_rivi;

          RAISE NOTICE 'kht_rivi: %', kht_rivi;
          IF khti.tot_alkanut < aikavali_alkupvm THEN
            -- jo laskutettu
            kht_laskutettu := kht_laskutettu + COALESCE(kht_rivi.summa, 0.0);
          ELSE
            -- laskutetaan nyt
            kht_laskutetaan := kht_laskutetaan + COALESCE(kht_rivi.summa, 0.0);
          END IF;
        END LOOP;

      -- Kokonaishintaiset aikavälillä indeksikorotuksen kanssa
      kht_laskutetaan := 0.0;

      FOR khti_laskutetaan IN SELECT lk.summa AS kht_summa
                              FROM lasku_kohdistus lk
                                     JOIN lasku l ON lk.lasku = l.id
                              WHERE lk.toimenpideinstanssi = t.tpi
                                AND lk.maksueratyyppi = 'kokonaishintainen'
                                AND lk.summa IS NOT NULL
                                AND l.erapaiva >= hk_alkupvm
                                AND l.erapaiva <= hk_loppupvm
                                AND l.erapaiva >= aikavali_alkupvm
                                AND l.erapaiva <= aikavali_loppupvm

        LOOP
          kht_laskutetaan := kht_laskutetaan + COALESCE(khti_laskutetaan.kht_summa, 0.0);
        END LOOP;

      -- Äkilliset hoitotyöt ja vanhinkojen korjaukset hoitokaudella
      aht_laskutettu := 0.0;
      aht_laskutetaan := 0.0;

      FOR ahti IN
        SELECT summa            as aht_summa,
               lk.suoritus_alku AS tot_alkanut,
               lk.id            AS id,
               lk.tehtava       AS toimenpidekoodi,
               NULL             as indeksi
        FROM lasku_kohdistus lk
               JOIN toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
        WHERE lk.maksueratyyppi = 'akillinen-hoitotyo'
          AND lk.poistettu IS NOT TRUE
          AND lk.suoritus_alku BETWEEN hk_alkupvm AND aikavali_loppupvm

        LOOP

          -- Indeksi ei käytössä, annetaan summa sellaisenaan
          SELECT ahti.aht_summa AS summa,
                 ahti.aht_summa AS korotettuna,
                 0::NUMERIC     as korotus
                 INTO aht_rivi;

          RAISE NOTICE 'aht_rivi: %', aht_rivi;
          IF ahti.tot_alkanut < aikavali_alkupvm THEN
            -- jo laskutettu
            aht_laskutettu := aht_laskutettu + COALESCE(aht_rivi.summa, 0.0);
          ELSE
            -- laskutetaan nyt
            aht_laskutetaan := aht_laskutetaan + COALESCE(aht_rivi.summa, 0.0);
          END IF;
        END LOOP;

      -- Kokonaishintaiset aikavälillä indeksikorotuksen kanssa
      aht_laskutetaan := 0.0;

      FOR ahti_laskutetaan IN SELECT lk.summa AS aht_summa
                              FROM lasku_kohdistus lk
                                     JOIN lasku l ON lk.lasku = l.id
                              WHERE lk.toimenpideinstanssi = t.tpi
                                AND lk.maksueratyyppi = 'akillinen-hoitotyo'
                                AND lk.summa IS NOT NULL
                                AND l.erapaiva >= hk_alkupvm
                                AND l.erapaiva <= hk_loppupvm
                                AND l.erapaiva >= aikavali_alkupvm
                                AND l.erapaiva <= aikavali_loppupvm

        LOOP
          aht_laskutetaan := aht_laskutetaan + COALESCE(ahti_laskutetaan.aht_summa, 0.0);
        END LOOP;

      -- Vahinkojen korjaukset
      mt_laskutettu := 0.0;
      mt_laskutetaan := 0.0;

      FOR mti IN
        SELECT summa            as mt_summa,
               lk.suoritus_alku AS tot_alkanut,
               lk.id            AS id,
               lk.tehtava       AS toimenpidekoodi,
               NULL             as indeksi
        FROM lasku_kohdistus lk
               JOIN toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
        WHERE lk.maksueratyyppi = 'muu' -- vahinkojen korjaukset
          AND lk.poistettu IS NOT TRUE
          AND lk.suoritus_alku BETWEEN hk_alkupvm AND aikavali_loppupvm

        LOOP

          -- Indeksi ei käytössä, annetaan summa sellaisenaan
          SELECT mti.mt_summa AS summa,
                 mti.mt_summa AS korotettuna,
                 0::NUMERIC   as korotus
                 INTO mt_rivi;

          RAISE NOTICE 'mt_rivi: %', mt_rivi;
          IF mti.tot_alkanut < aikavali_alkupvm THEN
            -- jo laskutettu
            mt_laskutettu := mt_laskutettu + COALESCE(mt_rivi.summa, 0.0);
          ELSE
            -- laskutetaan nyt
            mt_laskutetaan := mt_laskutetaan + COALESCE(mt_rivi.summa, 0.0);
          END IF;
        END LOOP;


      -- Hoitokaudella ennen aikaväliä laskutetut sanktiot
      sakot_laskutettu := 0.0;
      sakot_laskutetaan := 0.0;

      FOR sanktiorivi IN SELECT -maara AS maara, perintapvm, indeksi, perintapvm
                         FROM sanktio s
                         WHERE s.toimenpideinstanssi = t.tpi
                           AND s.maara IS NOT NULL
                           AND s.perintapvm >= hk_alkupvm
                           AND s.perintapvm <= aikavali_loppupvm
                           AND s.poistettu IS NOT TRUE
        LOOP
          SELECT *
          FROM laske_kuukauden_indeksikorotus((SELECT EXTRACT(YEAR FROM sanktiorivi.perintapvm) :: INTEGER),
                                              (SELECT EXTRACT(MONTH FROM sanktiorivi.perintapvm) :: INTEGER),
                                              sanktiorivi.indeksi, sanktiorivi.maara, perusluku)
               INTO sakot_rivi;
          IF sanktiorivi.perintapvm < aikavali_alkupvm THEN
            sakot_laskutettu := sakot_laskutettu + COALESCE(sakot_rivi.summa, 0.0);
          ELSE
            sakot_laskutetaan := sakot_laskutetaan + COALESCE(sakot_rivi.summa, 0.0);
          END IF;
        END LOOP;

      suolasakot_laskutettu := 0.0;
      suolasakot_laskutetaan := 0.0;

      SELECT *
      FROM suolasakko
      WHERE urakka = ur
        AND (SELECT EXTRACT(YEAR FROM hk_alkupvm) :: INTEGER) = hoitokauden_alkuvuosi INTO hoitokauden_suolasakko_rivi;

      hoitokauden_laskettu_suolasakon_maara = (SELECT hoitokauden_suolasakko(ur, hk_alkupvm, hk_loppupvm));


      -- Suolasakko lasketaan vain Talvihoito-toimenpiteelle (tuotekoodi '23100')
      IF t.tuotekoodi = '23100' THEN

        -- Jos suolasakko ei ole käytössä, ei edetä
        IF (hoitokauden_suolasakko_rivi.hoitokauden_alkuvuosi IS NULL AND
            hoitokauden_suolasakko_rivi.maksukuukausi IS NULL)
        THEN
          RAISE NOTICE 'Suolasakko ei käytössä annetulla aikavälillä urakassa %, aikavali_alkupvm: %, hoitokauden_suolasakko_rivi: %', ur, aikavali_alkupvm, hoitokauden_suolasakko_rivi;
          -- Suolasakko voi olla laskutettu jo hoitokaudella vain kk:ina 6-9 koska mahdolliset laskutus-kk:t ovat 5-9
        ELSIF (hoitokauden_suolasakko_rivi.maksukuukausi < (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER)
          AND (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER) < 10)
        THEN
          RAISE NOTICE 'Suolasakko on laskutettu aiemmin hoitokaudella kuukautena %', hoitokauden_suolasakko_rivi.maksukuukausi;
          suolasakot_laskutettu := hoitokauden_laskettu_suolasakko_rivi.summa;
          -- Jos valittu yksittäinen kuukausi on maksukuukausi TAI jos kyseessä koko hoitokauden raportti (poikkeustapaus)
        ELSIF (hoitokauden_suolasakko_rivi.maksukuukausi = (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER))
          OR (aikavali_alkupvm = hk_alkupvm AND aikavali_loppupvm = hk_loppupvm) THEN
          RAISE NOTICE 'Suolasakko laskutetaan tässä kuussa % tai kyseessä koko hoitokauden LYV-raportti.', hoitokauden_suolasakko_rivi.maksukuukausi;
          suolasakot_laskutetaan := hoitokauden_laskettu_suolasakko_rivi.summa;
        ELSE
          RAISE NOTICE 'Suolasakkoa ei vielä laskutettu, maksukuukauden arvo: %', hoitokauden_suolasakko_rivi.maksukuukausi;
        END IF;
      END IF;


      -- bonukset lasketaan erikseen tyypin perusteella
      bonukset_laskutettu := 0.0;
      bonukset_laskutetaan := 0.0;


      -- Onko suolasakko käytössä urakassa
      IF (select count(*)
          FROM suolasakko
          WHERE urakka = ur
            AND kaytossa
            AND hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM hk_alkupvm) :: INTEGER)) > 0
      THEN
        suolasakko_kaytossa = TRUE;
      ELSE
        suolasakko_kaytossa = FALSE;
      END IF;

      -- Ovatko suolasakon tarvitsemat lämpötilat kannassa
      SELECT * INTO lampotilat
      FROM lampotilat
      WHERE urakka = ur
        AND alkupvm = hk_alkupvm
        AND loppupvm = hk_loppupvm;

      IF (lampotilat IS NULL OR lampotilat.keskilampotila IS NULL OR lampotilat.pitka_keskilampotila IS NULL)
      THEN
        RAISE NOTICE 'Urakalle % ei ole lämpötiloja hoitokaudelle % - %', ur, hk_alkupvm, hk_loppupvm;
        RAISE NOTICE 'Keskilämpötila hoitokaudella %, pitkän ajan keskilämpötila %', lampotilat.keskilampotila, lampotilat.pitka_keskilampotila;
        lampotila_puuttuu = TRUE;
      ELSE
        lampotila_puuttuu = FALSE;
      END IF;

      -- Indeksisummia ei lasketa, indeksit eivät ole käytössä

      -- Kustannusten kokonaissummat
      kaikki_paitsi_kht_laskutettu := 0.0;
      kaikki_laskutettu := 0.0;

      kaikki_paitsi_kht_laskutetaan := 0.0;
      kaikki_laskutetaan := 0.0;

      kaikki_paitsi_kht_laskutettu := yht_laskutettu + sakot_laskutettu +
                                      COALESCE(suolasakot_laskutettu, 0.0) +
                                      aht_laskutettu +
                                      mt_laskutettu +
                                      bonukset_laskutettu + kht_laskutettu;

      kaikki_laskutettu := kaikki_paitsi_kht_laskutettu + kht_laskutettu;

      kaikki_paitsi_kht_laskutetaan := yht_laskutetaan + sakot_laskutetaan +
                                       COALESCE(suolasakot_laskutetaan, 0.0) +
                                       aht_laskutetaan +
                                       mt_laskutetaan +
                                       bonukset_laskutetaan  + kht_laskutetaan;
      kaikki_laskutetaan := kaikki_paitsi_kht_laskutetaan + kht_laskutetaan;


      RAISE NOTICE '
    Yhteenveto:';
      RAISE NOTICE 'LASKUTETTU ENNEN AIKAVÄLIÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
      RAISE NOTICE 'kit_laskutettu: %', kit_laskutettu;
      RAISE NOTICE 'kat_laskutettu: %', kat_laskutettu;
      RAISE NOTICE 'yht_laskutettu: %', yht_laskutettu;
      RAISE NOTICE 'kht_laskutettu: %', kht_laskutettu;
      RAISE NOTICE 'sakot_laskutettu: %', sakot_laskutettu;
      RAISE NOTICE 'suolasakot_laskutettu: %', suolasakot_laskutettu;
      RAISE NOTICE 'aht_laskutettu: %', aht_laskutettu;
      RAISE NOTICE 'mt_laskutettu: %', mt_laskutettu;
      RAISE NOTICE 'bonukset_laskutettu: %', bonukset_laskutettu;

      RAISE NOTICE '
    LASKUTETAAN AIKAVÄLILLÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
      RAISE NOTICE 'kit_laskutetaan: %', kit_laskutetaan;
      RAISE NOTICE 'kat_laskutetaan: %', kat_laskutetaan;
      RAISE NOTICE 'yht_laskutetaan: %', yht_laskutetaan;
      RAISE NOTICE 'kht_laskutetaan: %', kht_laskutetaan;
      RAISE NOTICE 'sakot_laskutetaan: %', sakot_laskutetaan;
      RAISE NOTICE 'suolasakot_laskutetaan: %', suolasakot_laskutetaan;
      RAISE NOTICE 'aht_laskutetaan: %', aht_laskutetaan;
      RAISE NOTICE 'mt_laskutetaan: %', mt_laskutetaan;
      RAISE NOTICE 'bonukset_laskutetaan: %', bonukset_laskutetaan;

      RAISE NOTICE 'kaikki_paitsi_kht_laskutettu: %', kaikki_paitsi_kht_laskutettu;
      RAISE NOTICE 'kaikki_laskutettu: %', kaikki_laskutettu;
      RAISE NOTICE 'kaikki_paitsi_kht_laskutetaan: %', kaikki_paitsi_kht_laskutetaan;
      RAISE NOTICE 'kaikki_laskutetaan: %', kaikki_laskutetaan;

      RAISE NOTICE 'suolasakko_kaytossa: %', suolasakko_kaytossa;
      RAISE NOTICE 'lampotila_puuttuu: %', lampotila_puuttuu;

      RAISE NOTICE '***** Käsitelly loppui toimenpiteelle: %  *****

    ', t.nimi;

      rivi := (t.nimi, t.tuotekoodi, t.tpi, perusluku,
               NULL, NULL,
               NULL, NULL,
               kaikki_paitsi_kht_laskutettu, kaikki_laskutettu,
               kaikki_paitsi_kht_laskutetaan, kaikki_laskutetaan,
               kht_laskutettu, NULL, NULL,
               kht_laskutetaan, NULL, NULL,
               yht_laskutettu, NULL, NULL,
               yht_laskutetaan, NULL, NULL,
               sakot_laskutettu, NULL, NULL,
               sakot_laskutetaan, NULL, NULL,
               suolasakot_laskutettu, NULL, NULL,
               suolasakot_laskutetaan, NULL, NULL,
               mt_laskutettu, NULL, NULL,
               mt_laskutetaan, NULL, NULL,
               aht_laskutettu, NULL,
               NULL,
               aht_laskutetaan, NULL,
               NULL,
               NULL, NULL,
               NULL,
               NULL, NULL,
               NULL,
               bonukset_laskutettu, NULL, NULL,
               bonukset_laskutetaan, NULL, NULL,
               suolasakko_kaytossa, lampotila_puuttuu,
               mt_laskutettu, NULL,
               NULL,
               mt_laskutetaan, NULL,NULL
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
  ON CONFLICT ON CONSTRAINT uniikki_urakka_aika DO UPDATE SET rivit = cache, tallennettu = NOW();
END;
$$;

