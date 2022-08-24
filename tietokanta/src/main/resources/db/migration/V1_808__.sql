-- Palauttaa maksuerien kokonaissummat
-- Tallentaa laskutusyhteenvetoon päivittyneen tilanteen
CREATE OR REPLACE FUNCTION laskutusyhteenveto_teiden_hoito(hk_alkupvm date, hk_loppupvm date, aikavali_alkupvm date,
                                                           aikavali_loppupvm date,
                                                           ur integer) returns SETOF laskutusyhteenveto_rivi
    language plpgsql
as
$$
DECLARE
    t                                     RECORD;
    perusluku                             NUMERIC; -- urakan indeksilaskennan perusluku (urakkasopimusta edeltävän vuoden joulukuusta 3kk ka)

    kaikki_paitsi_hankinnat_laskutettu    NUMERIC;
    kaikki_laskutettu                     NUMERIC;
    kaikki_paitsi_hankinnat_laskutetaan   NUMERIC;
    kaikki_laskutetaan                    NUMERIC;

    -- TODO:
    -- Talvihoito: Suolasanktiot
    -- MHU Hoidonjohto: Johto- ja hallintok, Erillishank, HJ-palkkio, bonukset, sanktiot


    -- HANKINNAT (= kiinteät, kustannusarvioidut ja hallinnolliset yhteensä).
    -- Lähetetään Sampoon kokonaishintaisessa maksuerässä.
    hankinnat_laskutettu                  NUMERIC;
    hankinnat_laskutetaan                 NUMERIC;
    hankinnat_indeksilla                  RECORD;
    hankinnat_rivi                        RECORD;
    hankinnat_indeksilla_laskutetaan      RECORD;

    -- Äkilliset hoitotyöt
    akilliset_laskutettu                  NUMERIC;
    akilliset_laskutetaan                 NUMERIC;
    akilliset_indeksilla                  RECORD;
    akilliset_rivi                        RECORD;
    akilliset_indeksilla_laskutetaan      RECORD;

    -- Vahinkojen korjaukset
    vahingot_laskutettu                   NUMERIC;
    vahingot_laskutetaan                  NUMERIC;
    vahingot_indeksilla                   RECORD;
    vahingot_rivi                         RECORD;

    -- Lisätyöt
    lisatyot_laskutettu                   NUMERIC;
    lisatyot_laskutetaan                  NUMERIC;
    lisatyot_indeksilla                   RECORD;
    lisatyot_rivi                         RECORD;

    -- Sakot
    sakot_laskutettu                      NUMERIC;
    sakot_rivi                            RECORD;
    sakot_laskutetaan                     NUMERIC;
    sanktiorivi                           RECORD;

    -- Suolasakot
    suolasakot_laskutettu                 NUMERIC;
    suolasakot_laskutetaan                NUMERIC;
    hoitokauden_suolasakko_rivi           RECORD;
    hoitokauden_laskettu_suolasakko_rivi  indeksitarkistettu_suolasakko_rivi;
    hoitokauden_laskettu_suolasakon_maara NUMERIC;

    -- Bonukset
    bonukset_laskutettu                   NUMERIC;
    bonukset_laskutetaan                  NUMERIC;
    suolasakko_kaytossa                   BOOLEAN;
    lampotilat                            RECORD;
    lampotila_puuttuu                     BOOLEAN;
    cache                                 laskutusyhteenveto_rivi[];
    rivi                                  laskutusyhteenveto_rivi;
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

            hankinnat_laskutettu := 0.0;


            -- Hoitokaudella ennen aikaväliä laskutetut kokonaishintaisten töiden kustannukset
            -- Kokonaishintaisten töiden maksuerään lasketaan kaikki hankinnat = kaikki hankintakustannukset yhteensä =
            -- Kiinteähintaiset työt + Kustannusarvioidut (määrämitattavat) työt + Hallinnolliset toimenpiteet

            -- Kiinteähintaisia töitä käsitellään maanteidenhoidon urakoissa (MHU) samaan tapaan kuin kokonaishintaisia töitä hoitourakoissa.
            -- Kustannukset suunnitellaan ja suunniteltu kustannus siirtyy automaattisesti myös maksueräksi.
            -- Kiinteähintaisia kuluja ei kirjata kulujen kirjauksessa kuten kustannussuunniteltuja, määrämitattavia töitä ja lisätöitä kirjataan.

            -- Kulujen kirjauksen kautta syötetyille maksuerille ei lasketa indeksiä.
            -- TODO: Lasketaanko indeksi kiinteähintaisille töille??


            -- Teiden hoidon urakoissa kokonaishintaisiin töihin lasketaan kiinteähintaiset, kustannusarvioidut ja yksikköhintaiset (sic) työt
            hankinnat_laskutettu := 0.0;
            hankinnat_laskutetaan := 0.0;

            FOR hankinnat_indeksilla IN
                SELECT summa      as hankinnat_summa,
                       l.erapaiva AS tot_alkanut,
                       lk.id      AS id,
                       lk.tehtava AS toimenpidekoodi,
                       NULL       as indeksi -- TODO: Pitäisikö kiinteähintaisissa laskea indeksi?
                FROM lasku_kohdistus lk
                         JOIN toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
                         JOIN lasku l on lk.lasku = l.id
                WHERE lk.maksueratyyppi = 'kokonaishintainen' -- Sisältää kustannusarvioituihin (määrämitattaviin) töihin sekä hallintokorvauksiin syötetyt kustannukset ja yksikkohintaiset työt.
                  AND lk.poistettu IS NOT TRUE
                  AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm

                LOOP

                    -- Indeksi ei käytössä, annetaan summa sellaisenaan
                    SELECT hankinnat_indeksilla.hankinnat_summa AS summa,
                           hankinnat_indeksilla.hankinnat_summa AS korotettuna,
                           0::NUMERIC     as korotus
                    INTO hankinnat_rivi;

                    RAISE NOTICE 'hankinnat_rivi: %', hankinnat_rivi;
                    IF hankinnat_indeksilla.tot_alkanut < aikavali_alkupvm THEN
                        -- jo laskutettu
                        hankinnat_laskutettu := hankinnat_laskutettu + COALESCE(hankinnat_rivi.summa, 0.0);
                    ELSE
                        -- laskutetaan nyt
                        hankinnat_laskutetaan := hankinnat_laskutetaan + COALESCE(hankinnat_rivi.summa, 0.0);
                    END IF;
                END LOOP;

            -- Kokonaishintaiset aikavälillä indeksikorotuksen kanssa
            hankinnat_laskutetaan := 0.0;

            FOR hankinnat_indeksilla_laskutetaan IN SELECT lk.summa AS hankinnat_summa
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
                    hankinnat_laskutetaan := hankinnat_laskutetaan + COALESCE(hankinnat_indeksilla_laskutetaan.hankinnat_summa, 0.0);
                END LOOP;

            -- Äkilliset hoitotyöt hoitokaudella
            akilliset_laskutettu := 0.0;
            akilliset_laskutetaan := 0.0;

            FOR akilliset_indeksilla IN
                SELECT summa      as akilliset_summa,
                       l.erapaiva AS tot_alkanut,
                       lk.id      AS id,
                       lk.tehtava AS toimenpidekoodi,
                       NULL       as indeksi
                FROM lasku_kohdistus lk
                         JOIN toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
                         JOIN lasku l on lk.lasku = l.id
                WHERE lk.tehtavaryhma in (SELECT id
                                          from tehtavaryhma
                                          where nimi in ('Äkilliset hoitotyöt, Talvihoito (T1)',
                                                         'Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)',
                                                         'Äkilliset hoitotyöt, Soratiet (T1)'))
                  AND lk.poistettu IS NOT TRUE
                  AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm

                LOOP

                    -- Indeksi ei käytössä, annetaan summa sellaisenaan
                    SELECT akilliset_indeksilla.akilliset_summa AS summa,
                           akilliset_indeksilla.akilliset_summa AS korotettuna,
                           0::NUMERIC     as korotus
                    INTO akilliset_rivi;

                    RAISE NOTICE 'akilliset_rivi: %', akilliset_rivi;
                    IF akilliset_indeksilla.tot_alkanut < aikavali_alkupvm THEN
                        -- jo laskutettu
                        akilliset_laskutettu := akilliset_laskutettu + COALESCE(akilliset_rivi.summa, 0.0);
                    ELSE
                        -- laskutetaan nyt
                        akilliset_laskutetaan := akilliset_laskutetaan + COALESCE(akilliset_rivi.summa, 0.0);
                    END IF;
                END LOOP;

            -- Äkilliset hoitotyöt aikavälillä indeksikorotuksen kanssa
            akilliset_laskutetaan := 0.0;

            FOR akilliset_indeksilla_laskutetaan IN SELECT lk.summa AS akilliset_summa
                                    FROM lasku_kohdistus lk
                                             JOIN lasku l ON lk.lasku = l.id
                                    WHERE lk.toimenpideinstanssi = t.tpi
                                      AND lk.tehtavaryhma in (SELECT id
                                                              from tehtavaryhma
                                                              where nimi in ('Äkilliset hoitotyöt, Talvihoito (T1)',
                                                                             'Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)',
                                                                             'Äkilliset hoitotyöt, Soratiet (T1)'))
                                      AND lk.summa IS NOT NULL
                                      AND l.erapaiva >= hk_alkupvm
                                      AND l.erapaiva <= hk_loppupvm
                                      AND l.erapaiva >= aikavali_alkupvm
                                      AND l.erapaiva <= aikavali_loppupvm

                LOOP
                    akilliset_laskutetaan := akilliset_laskutetaan + COALESCE(akilliset_indeksilla_laskutetaan.akilliset_summa, 0.0);
                END LOOP;

            -- Vahinkojen korjaukset
            vahingot_laskutettu := 0.0;
            vahingot_laskutetaan := 0.0;

            FOR vahingot_indeksilla IN
                SELECT summa      as akilliset_summa,
                       l.erapaiva AS tot_alkanut,
                       lk.id      AS id,
                       lk.tehtava AS toimenpidekoodi,
                       NULL       as indeksi
                FROM lasku_kohdistus lk
                         JOIN toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
                         JOIN lasku l on lk.lasku = l.id
                WHERE lk.tehtavaryhma in (SELECT id
                                          from tehtavaryhma
                                          where nimi in ('Vahinkojen korjaukset, Talvihoito (T2)',
                                                         'Vahinkojen korjaukset, Liikenneympäristön hoito (T2)',
                                                         'Vahinkojen korjaukset, Soratiet (T2)'))
                  AND lk.poistettu IS NOT TRUE
                  AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
                LOOP

                    -- Indeksi ei käytössä, annetaan summa sellaisenaan
                    SELECT vahingot_indeksilla.vahingot_summa AS summa,
                           vahingot_indeksilla.vahingot_summa AS korotettuna,
                           0::NUMERIC   as korotus
                    INTO vahingot_rivi;

                    RAISE NOTICE 'vahingot_rivi: %', vahingot_rivi;
                    IF vahingot_indeksilla.tot_alkanut < aikavali_alkupvm THEN
                        -- jo laskutettu
                        vahingot_laskutettu := vahingot_laskutettu + COALESCE(vahingot_rivi.summa, 0.0);
                    ELSE
                        -- laskutetaan nyt
                        vahingot_laskutetaan := vahingot_laskutetaan + COALESCE(vahingot_rivi.summa, 0.0);
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
              AND (SELECT EXTRACT(YEAR FROM hk_alkupvm) :: INTEGER) = hoitokauden_alkuvuosi
            INTO hoitokauden_suolasakko_rivi;

            hoitokauden_laskettu_suolasakon_maara = (SELECT hoitokauden_suolasakko(ur, hk_alkupvm, hk_loppupvm));


            -- Suolasakko lasketaan vain Talvihoito-toimenpiteelle (tuotekoodi '23100')
            IF t.tuotekoodi = '23100' THEN

                -- Jos suolasakko ei ole käytössä, ei edetä
                IF (hoitokauden_suolasakko_rivi.hoitokauden_alkuvuosi IS NULL AND
                    hoitokauden_suolasakko_rivi.maksukuukausi IS NULL)
                THEN
                    RAISE NOTICE 'Suolasakko ei käytössä annetulla aikavälillä urakassa %, aikavali_alkupvm: %, hoitokauden_suolasakko_rivi: %', ur, aikavali_alkupvm, hoitokauden_suolasakko_rivi;
                    -- Suolasakko voi olla laskutettu jo hoitokaudella vain kk:ina 6-9 koska mahdolliset laskutus-kk:t ovat 5-9
                ELSIF (hoitokauden_suolasakko_rivi.maksukuukausi <
                       (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER)
                    AND (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER) < 10)
                THEN
                    RAISE NOTICE 'Suolasakko on laskutettu aiemmin hoitokaudella kuukautena %', hoitokauden_suolasakko_rivi.maksukuukausi;
                    suolasakot_laskutettu := hoitokauden_laskettu_suolasakko_rivi.summa;
                    -- Jos valittu yksittäinen kuukausi on maksukuukausi TAI jos kyseessä koko hoitokauden raportti (poikkeustapaus)
                ELSIF (hoitokauden_suolasakko_rivi.maksukuukausi =
                       (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER))
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
            SELECT *
            INTO lampotilat
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
            kaikki_paitsi_hankinnat_laskutettu := 0.0;
            kaikki_laskutettu := 0.0;

            kaikki_paitsi_hankinnat_laskutetaan := 0.0;
            kaikki_laskutetaan := 0.0;

            kaikki_paitsi_hankinnat_laskutettu := sakot_laskutettu +
                                            COALESCE(suolasakot_laskutettu, 0.0) +
                                            akilliset_laskutettu +
                                            vahingot_laskutettu +
                                            bonukset_laskutettu + hankinnat_laskutettu;

            kaikki_laskutettu := kaikki_paitsi_hankinnat_laskutettu + hankinnat_laskutettu;

            kaikki_paitsi_hankinnat_laskutetaan := sakot_laskutetaan +
                                             COALESCE(suolasakot_laskutetaan, 0.0) +
                                             akilliset_laskutetaan +
                                             vahingot_laskutetaan +
                                             bonukset_laskutetaan + hankinnat_laskutetaan;
            kaikki_laskutetaan := kaikki_paitsi_hankinnat_laskutetaan + hankinnat_laskutetaan;


            RAISE NOTICE '
    Yhteenveto:';
            RAISE NOTICE 'LASKUTETTU ENNEN AIKAVÄLIÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
            RAISE NOTICE 'hankinnat_laskutettu: %', hankinnat_laskutettu;
            RAISE NOTICE 'sakot_laskutettu: %', sakot_laskutettu;
            RAISE NOTICE 'suolasakot_laskutettu: %', suolasakot_laskutettu;
            RAISE NOTICE 'akilliset_laskutettu: %', akilliset_laskutettu;
            RAISE NOTICE 'vahingot_laskutettu: %', vahingot_laskutettu;
            RAISE NOTICE 'bonukset_laskutettu: %', bonukset_laskutettu;

            RAISE NOTICE 'LASKUTETAAN AIKAVÄLILLÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
            RAISE NOTICE 'hankinnat_laskutetaan: %', hankinnat_laskutetaan;
            RAISE NOTICE 'sakot_laskutetaan: %', sakot_laskutetaan;
            RAISE NOTICE 'suolasakot_laskutetaan: %', suolasakot_laskutetaan;
            RAISE NOTICE 'akilliset_laskutetaan: %', akilliset_laskutetaan;
            RAISE NOTICE 'vahingot_laskutetaan: %', vahingot_laskutetaan;
            RAISE NOTICE 'bonukset_laskutetaan: %', bonukset_laskutetaan;

            RAISE NOTICE 'kaikki_paitsi_hankinnat_laskutettu: %', kaikki_paitsi_hankinnat_laskutettu;
            RAISE NOTICE 'kaikki_laskutettu: %', kaikki_laskutettu;
            RAISE NOTICE 'kaikki_paitsi_hankinnat_laskutetaan: %', kaikki_paitsi_hankinnat_laskutetaan;
            RAISE NOTICE 'kaikki_laskutetaan: %', kaikki_laskutetaan;

            RAISE NOTICE 'suolasakko_kaytossa: %', suolasakko_kaytossa;
            RAISE NOTICE 'lampotila_puuttuu: %', lampotila_puuttuu;

            RAISE NOTICE '***** Käsitelly loppui toimenpiteelle: %  *****

    ', t.nimi;

            rivi := (t.nimi, t.tuotekoodi, t.tpi, perusluku,
                     NULL, NULL,
                     NULL, NULL,
                     kaikki_paitsi_hankinnat_laskutettu, kaikki_laskutettu,
                     kaikki_paitsi_hankinnat_laskutetaan, kaikki_laskutetaan,
                     hankinnat_laskutettu, NULL, NULL,
                     hankinnat_laskutetaan, NULL, NULL,
                     NULL, NULL, NULL,
                     NULL, NULL, NULL,
                     sakot_laskutettu, NULL, NULL,
                     sakot_laskutetaan, NULL, NULL,
                     suolasakot_laskutettu, NULL, NULL,
                     suolasakot_laskutetaan, NULL, NULL,
                     vahingot_laskutettu, NULL, NULL,
                     vahingot_laskutetaan, NULL, NULL,
                     akilliset_laskutettu, NULL,
                     NULL,
                     akilliset_laskutetaan, NULL,
                     NULL,
                     NULL, NULL,
                     NULL,
                     NULL, NULL,
                     NULL,
                     bonukset_laskutettu, NULL, NULL,
                     bonukset_laskutetaan, NULL, NULL,
                     suolasakko_kaytossa, lampotila_puuttuu,
                     vahingot_laskutettu, NULL,
                     NULL,
                     vahingot_laskutetaan, NULL, NULL
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

