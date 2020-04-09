-- Palauttaa maksuerien kokonaissummat
-- Tallentaa laskutusyhteenvetoon päivittyneen tilanteen
CREATE OR REPLACE FUNCTION laskutusyhteenveto_teiden_hoito(hk_alkupvm date, hk_loppupvm date, aikavali_alkupvm date,
                                                           aikavali_loppupvm date,
                                                           ur integer)
    RETURNS SETOF laskutusyhteenveto_rivi AS
$$
DECLARE

    -- Proseduuri on kaapattu hoitourakoista MH-urakoihin. Se palauttaa tiedot samassa muodossa kuin vanhoissa urakoissa, vaikka laskentalogiikka on toisenlainen.
    -- MH-urakoissa lähetetään Sampoon vain yksi maksuerä (kokonaishintainen).
    -- TODO: Sakkoja ja bonuksia täytyy päivittää, kun tilanne selkenee. Nyt lähetetään Sampoon tavalliset sakot ja asiakatyytyväisyysbonukset, jotka on syötetty vanhalla mallilla.

    --  Proseduurin palauttamaa tulosta käytetään ainakin maksuerien Sampo-lähetyksessä ja Maksuerä-näkymässä,
    --  mahdollisesti myös laskutusyhteenveto-raportilla (ainakin mutkan eli laskutustyhteenveto_cache-taulun kautta).


    t                                       RECORD;
    ind                                     VARCHAR; -- hoitourakassa käytettävä indeksi
    perusluku                               NUMERIC; -- urakan indeksilaskennan perusluku

    kaikki_paitsi_kohdistetut_laskutettu    NUMERIC;
    kaikki_laskutettu                       NUMERIC;
    kaikki_paitsi_kohdistetut_laskutetaan   NUMERIC;
    kaikki_laskutetaan                      NUMERIC;

    -- Hoidonjohdon kustannukset lasketaan kustannussuunnitelmasta automaattisesti maksuerään. Vain poikkeustilanteissa kuluja kirjataan kustannusten kohdistuksessa.
    -- Kulu siirtyy suunnitelmasta maksuerään vasta kuukauden viimeisenä päivänä.
    -- Kustannussuunnitelmasta maksuerään siirtyvään kuluun lasketaan indeksikorotus.
    hoidonjohto_laskutettu                  NUMERIC;
    hoidonjohto_laskutettu_ind_korotettuna  NUMERIC;
    hoidonjohto_laskutettu_ind_korotus      NUMERIC;
    hoidonjohto_laskutetaan                 NUMERIC;
    hoidonjohto_laskutetaan_ind_korotettuna NUMERIC;
    hoidonjohto_laskutetaan_ind_korotus     NUMERIC;
    hoidonjohtoi                            RECORD;
    hoidonjohtoi_laskutetaan                RECORD;

    -- Kohdistetut tarkoittaa tässä kulujen kohdistuksen kautta kirjattuja kustannuksia.
    -- Mukaan lasketaan tavalliset kulut ja lisätöihin liittyvät kulut.
    -- Myös äkillisten hoitotöiden, vahinkojen korjausten ja tilaajan rahavarauksiin liittyvät kohdistetut kulut otetaan mukaan.
    -- Kulujen kohdistusten kautta syötettyihin kustannuksiin ei lasketa indeksikorotusta.
    kohdistetut_laskutettu                  NUMERIC;
    kohdistetut_laskutetaan                 NUMERIC;
    kohdistetut                             RECORD;
    kohdistettu                             RECORD;

    -- Sakot (tavalliset Sankiot-näkymän kautta kirjatut sakot)
    sakot_laskutettu                        NUMERIC;
    sakot_laskutettu_ind_korotettuna        NUMERIC;
    sakot_laskutettu_ind_korotus            NUMERIC;
    sakot_rivi                              RECORD;
    sakot_laskutetaan                       NUMERIC;
    sakot_laskutetaan_ind_korotettuna       NUMERIC;
    sakot_laskutetaan_ind_korotus           NUMERIC;
    sanktiorivi                             RECORD;

    -- Suolasakot
    suolasakot_laskutettu                   NUMERIC;
    suolasakot_laskutettu_ind_korotettuna   NUMERIC;
    suolasakot_laskutettu_ind_korotus       NUMERIC;
    suolasakot_laskutetaan                  NUMERIC;
    suolasakot_laskutetaan_ind_korotettuna  NUMERIC;
    suolasakot_laskutetaan_ind_korotus      NUMERIC;
    hoitokauden_suolasakko_rivi             RECORD;
    hoitokauden_laskettu_suolasakko_rivi    indeksitarkistettu_suolasakko_rivi;
    hoitokauden_laskettu_suolasakon_maara   NUMERIC;

    -- Bonukset (tavalliset Erillishankinnat-näkymän kautta kirjatut bonukset)
    eki                                     RECORD; -- Bonukset haetaan erillishankinnat-taulusta
    bonukset_laskutettu                     NUMERIC;
    bonukset_laskutettu_ind_korotettuna     NUMERIC;
    bonukset_laskutettu_ind_korotus         NUMERIC;
    bonukset_rivi                           RECORD;
    bonukset_laskutetaan                    NUMERIC;
    bonukset_laskutetaan_ind_korotettuna    NUMERIC;
    bonukset_laskutetaan_ind_korotus        NUMERIC;
    suolasakko_kaytossa                     BOOLEAN;
    lampotilat                              RECORD;
    lampotila_puuttuu                       BOOLEAN;
    cache                                   laskutusyhteenveto_rivi[];
    rivi                                    laskutusyhteenveto_rivi;
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

    -- Haetaan urakan indeksiin liittyvät tiedot
    perusluku := indeksilaskennan_perusluku(ur);
    SELECT indeksi FROM urakka WHERE id = ur INTO ind;

    -- Loopataan urakan toimenpideinstanssien läpi
    -- MH-urakoissa on 7 toimenpideinstanssia:
    -- Talvihoito, liikenneympäristön hoito, sorateiden hoito, päällystykset, mhu-ylläpito, mhu-korjaukset ja mhu-hoidonjohto
    -- Hoidonjohtoon kustannuksia lasketaan suoraan suunnitelmasta (johto_ja_hallintokorvaus, kustannusarvitu_tyo, muutoin kustannukset tulevat kulujen kirjauskesta (lasku, lasku_kohdistus).
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

            -- HOIDON JOHTO, tpk 23150.
            -- Hoidon johdon kustannukset eli johto- ja hallintokorvays, erillishankinnat ja hoidonjohtopalkkio lasketaan maksuerään suoraan
            -- kustannussuunnitelmasta. Suunniteltu rahasumma siirtyy maksuerään kuukauden viimeisenä päivänä.
            -- Poikkeustapauksissa hoidon johdon kustannuksia kirjataan kulujen kohdistuksessa. Tällöin kustannukset lasketaan mukaan samaan tapaan kuin
            -- muutkin hankinnat (ks. kohdistetut_laskutetaan alla).
            hoidonjohto_laskutettu := 0.0;
            hoidonjohto_laskutetaan := 0.0;
            hoidonjohto_laskutettu_ind_korotettuna := 0.0;
            hoidonjohto_laskutettu_ind_korotus := 0.0;

            IF (t.tuotekoodi = '23150') THEN

                RAISE NOTICE 'Hoidonjohdon kustannuket lasketaan mukaan, koska toimenpideinstanssi on hoidon johto. %', t.tpi;

                -- Ennen tarkasteltavaa aikaväliä laskutetut hoidonjohdon kustannukset
                -- Käydään läpi tiedot tauluista johto_ja_hallintokorvaus ja kustannusarvioitu_tyo

                -- johto_ja_hallintokorvaus
                FOR hoidonjohtoi IN
                    SELECT id,
                           coalesce(tunnit, 0) * coalesce(tuntipalkka, 0)   as hoidonjohto_summa,
                           (SELECT (date_trunc('MONTH', format('%s-%s-%s', vuosi, kuukausi, 1)::DATE) +
                                    INTERVAL '1 MONTH - 1 day')::DATE)      as tot_alkanut,
                           (SELECT korotus
                            FROM laske_kuukauden_indeksikorotus(jhk.vuosi, jhk.kuukausi, ind,
                                                                coalesce(jhk.tunnit, 0) * coalesce(jhk.tuntipalkka, 0),
                                                                perusluku)) AS ind,
                           (SELECT korotettuna
                            FROM laske_kuukauden_indeksikorotus(jhk.vuosi, jhk.kuukausi, ind,
                                                                coalesce(jhk.tunnit, 0) * coalesce(jhk.tuntipalkka, 0),
                                                                perusluku)) AS kor
                    FROM johto_ja_hallintokorvaus jhk
                    WHERE "urakka-id" = ur
                      AND ((vuosi >= date_part('year', hk_alkupvm::DATE)
                        AND vuosi < date_part('year', aikavali_loppupvm::DATE))
                        OR (vuosi = date_part('year', aikavali_loppupvm::DATE) AND
                            kuukausi <= (SELECT CASE
                                                    WHEN
                                                        (aikavali_loppupvm::DATE =
                                                         (SELECT (date_trunc('MONTH', aikavali_loppupvm::DATE) +
                                                                  INTERVAL '1 MONTH - 1 day')::DATE))
                                                        THEN
                                                        date_part('MONTH', aikavali_loppupvm::DATE)::INTEGER
                                                    ELSE
                                                        (date_part('MONTH', aikavali_loppupvm::DATE)::INTEGER - 1)
                                                    END)))
                    LOOP
                        hoidonjohto_laskutettu :=
                                hoidonjohto_laskutettu + COALESCE(hoidonjohtoi.hoidonjohto_summa, 0.0);
                        hoidonjohto_laskutettu_ind_korotettuna :=
                                hoidonjohto_laskutettu_ind_korotettuna + hoidonjohtoi.kor;
                        hoidonjohto_laskutettu_ind_korotus := hoidonjohto_laskutettu_ind_korotus + hoidonjohtoi.ind;
                    END LOOP;

                -- kustannusarvioitu_tyo
                FOR hoidonjohtoi IN
                    SELECT id,
                           coalesce(summa, 0)                               as hoidonjohto_summa,
                           (SELECT (date_trunc('MONTH', format('%s-%s-%s', vuosi, kuukausi, 1)::DATE) +
                                    INTERVAL '1 MONTH - 1 day')::DATE)      as tot_alkanut,
                           (SELECT korotus
                            FROM laske_kuukauden_indeksikorotus(kat.vuosi, kat.kuukausi, ind,
                                                                coalesce(kat.summa, 0),
                                                                perusluku)) AS ind,
                           (SELECT korotettuna
                            FROM laske_kuukauden_indeksikorotus(kat.vuosi, kat.kuukausi, ind,
                                                                coalesce(summa, 0),
                                                                perusluku)) AS kor
                    FROM kustannusarvioitu_tyo kat
                    WHERE toimenpideinstanssi = t.tpi
                      AND ((vuosi >= date_part('year', hk_alkupvm::DATE)
                        AND vuosi < date_part('year', aikavali_loppupvm::DATE))
                        OR (vuosi = date_part('year', aikavali_loppupvm::DATE) AND
                            kuukausi <= (SELECT CASE
                                                    WHEN
                                                        (aikavali_loppupvm::DATE =
                                                         (SELECT (date_trunc('MONTH', aikavali_loppupvm::DATE) +
                                                                  INTERVAL '1 MONTH - 1 day')::DATE))
                                                        THEN
                                                        date_part('MONTH', aikavali_loppupvm::DATE)::INTEGER
                                                    ELSE
                                                        (date_part('MONTH', aikavali_loppupvm::DATE)::INTEGER - 1)
                                                    END)))

                    LOOP
                        hoidonjohto_laskutettu :=
                                hoidonjohto_laskutettu + COALESCE(hoidonjohtoi.hoidonjohto_summa, 0.0);
                        hoidonjohto_laskutettu_ind_korotettuna :=
                                hoidonjohto_laskutettu_ind_korotettuna + hoidonjohtoi.kor;
                        hoidonjohto_laskutettu_ind_korotus := hoidonjohto_laskutettu_ind_korotus + hoidonjohtoi.ind;
                    END LOOP;


                -- Tarkasteltavalla aikavälillä laskutetut tai laskutettavat hoidonjohdon kustannukset
                -- Käydään läpi tiedot tauluista johto_ja_hallintokorvaus ja kustannusarvioitu_tyo
                -- Kuluvan kuukauden laskutettava summa nousee maksuerään vasta kuukauden viimeisenä päivänä.

                hoidonjohto_laskutetaan := 0.0;

                -- johto_ja_hallintokorvaus
                FOR hoidonjohtoi_laskutetaan IN
                    SELECT id,
                           kuukausi,
                           vuosi,
                           coalesce(tunnit, 0) * coalesce(tuntipalkka, 0)   as hoidonjohto_summa,
                           (SELECT (date_trunc('MONTH', format('%s-%s-%s', vuosi, kuukausi, 1)::DATE) +
                                    INTERVAL '1 MONTH - 1 day')::DATE)      as tot_alkanut,
                           (SELECT korotus
                            FROM laske_kuukauden_indeksikorotus(jhk.vuosi, jhk.kuukausi, ind,
                                                                coalesce(jhk.tunnit, 0) * coalesce(jhk.tuntipalkka, 0),
                                                                perusluku)) AS ind,
                           (SELECT korotettuna
                            FROM laske_kuukauden_indeksikorotus(jhk.vuosi, jhk.kuukausi, ind,
                                                                coalesce(jhk.tunnit, 0) * coalesce(jhk.tuntipalkka, 0),
                                                                perusluku)) AS kor
                    FROM johto_ja_hallintokorvaus jhk
                    WHERE "urakka-id" = ur
                      AND ((vuosi BETWEEN date_part('year', aikavali_alkupvm::DATE) AND date_part('year', aikavali_loppupvm::DATE))
                        AND ((vuosi = date_part('year', aikavali_alkupvm::DATE) AND
                              kuukausi BETWEEN date_part('month', aikavali_alkupvm::DATE) AND
                                  (SELECT CASE
                                              WHEN
                                                  (date_part('year', aikavali_alkupvm::DATE) =
                                                   date_part('year', aikavali_loppupvm::DATE))
                                                  THEN
                                                  date_part('month', aikavali_loppupvm::DATE)::INTEGER
                                              ELSE
                                                  12
                                              END))
                            OR (vuosi = date_part('year', aikavali_loppupvm::DATE) AND kuukausi BETWEEN
                                (SELECT CASE
                                            WHEN
                                                (date_part('year', aikavali_loppupvm::DATE) =
                                                 date_part('year', aikavali_alkupvm::DATE))
                                                THEN
                                                date_part('month', aikavali_alkupvm::DATE)::INTEGER
                                            ELSE
                                                1
                                            END)
                                AND date_part('month', aikavali_loppupvm::DATE))
                            OR (vuosi != date_part('year', aikavali_alkupvm::DATE) AND
                                vuosi != date_part('year', aikavali_loppupvm::DATE) AND
                                kuukausi BETWEEN 1 AND 12)))

                    LOOP
                        -- Kuukauden laskutettava määrä päivittyy laskutettavaan summaan ja lähetettävään maksuerään vasta kuukauden viimeisenä päivänä.
                        IF (hoidonjohtoi_laskutetaan.tot_alkanut::DATE <= current_date) THEN
                            hoidonjohto_laskutetaan :=
                                        hoidonjohto_laskutetaan +
                                        COALESCE(hoidonjohtoi_laskutetaan.hoidonjohto_summa, 0.0);
                            hoidonjohto_laskutetaan_ind_korotettuna :=
                                    hoidonjohto_laskutetaan_ind_korotettuna + hoidonjohtoi_laskutetaan.kor;
                            hoidonjohto_laskutetaan_ind_korotus :=
                                    hoidonjohto_laskutetaan_ind_korotus + hoidonjohtoi_laskutetaan.ind;
                        END IF;
                    END LOOP;

                -- kustannusarvioitu_tyo
                FOR hoidonjohtoi_laskutetaan IN
                    SELECT id,
                           coalesce(summa, 0)                               as hoidonjohto_summa,
                           (SELECT (date_trunc('MONTH', format('%s-%s-%s', vuosi, kuukausi, 1)::DATE) +
                                    INTERVAL '1 MONTH - 1 day')::DATE)      as tot_alkanut,
                           (SELECT korotus
                            FROM laske_kuukauden_indeksikorotus(kat.vuosi, kat.kuukausi, ind,
                                                                coalesce(kat.summa, 0),
                                                                perusluku)) AS ind,
                           (SELECT korotettuna
                            FROM laske_kuukauden_indeksikorotus(kat.vuosi, kat.kuukausi, ind,
                                                                coalesce(summa, 0),
                                                                perusluku)) AS kor
                    FROM kustannusarvioitu_tyo kat
                    WHERE toimenpideinstanssi = t.tpi
                      AND ((vuosi BETWEEN date_part('year', aikavali_alkupvm::DATE) AND date_part('year', aikavali_loppupvm::DATE))
                        AND ((vuosi = date_part('year', aikavali_alkupvm::DATE) AND
                              kuukausi BETWEEN date_part('month', aikavali_alkupvm::DATE) AND
                                  (SELECT CASE
                                              WHEN
                                                  (date_part('year', aikavali_alkupvm::DATE) =
                                                   date_part('year', aikavali_loppupvm::DATE))
                                                  THEN
                                                  date_part('month', aikavali_loppupvm::DATE)::INTEGER
                                              ELSE
                                                  12
                                              END))
                            OR (vuosi = date_part('year', aikavali_loppupvm::DATE) AND kuukausi BETWEEN
                                (SELECT CASE
                                            WHEN
                                                (date_part('year', aikavali_loppupvm::DATE) =
                                                 date_part('year', aikavali_alkupvm::DATE))
                                                THEN
                                                date_part('month', aikavali_alkupvm::DATE)::INTEGER
                                            ELSE
                                                1
                                            END)
                                AND date_part('month', aikavali_loppupvm::DATE))
                            OR (vuosi != date_part('year', aikavali_alkupvm::DATE) AND
                                vuosi != date_part('year', aikavali_loppupvm::DATE) AND
                                kuukausi BETWEEN 1 AND 12)))

                    LOOP
                        -- Kuukauden laskutettava määrä päivittyy laskutettavaan summaan ja lähetettävään maksuerään vasta kuukauden viimeisenä päivänä.
                        IF (hoidonjohtoi_laskutetaan.tot_alkanut::DATE <= current_date) THEN
                            hoidonjohto_laskutetaan :=
                                        hoidonjohto_laskutetaan +
                                        COALESCE(hoidonjohtoi_laskutetaan.hoidonjohto_summa, 0.0);
                            hoidonjohto_laskutetaan_ind_korotettuna :=
                                    hoidonjohto_laskutetaan_ind_korotettuna + hoidonjohtoi_laskutetaan.kor;
                            hoidonjohto_laskutetaan_ind_korotus :=
                                    hoidonjohto_laskutetaan_ind_korotus + hoidonjohtoi_laskutetaan.ind;
                        END IF;
                    END LOOP;

            END IF; -- tuotekoodi = 23150 (Hoidonjohto)


            -- Kulujen kohdistuksesta maksuerään päätyvät kulut. Koskee kaikkia toimenpideinstansseja.
            kohdistetut_laskutettu := 0.0;
            kohdistetut_laskutetaan := 0.0;

            -- lasku_kohdistus
            FOR kohdistetut IN
                SELECT summa      as summa,
                       l.erapaiva AS tot_alkanut,
                       lk.id      AS id,
                       lk.tehtava AS toimenpidekoodi
                FROM lasku_kohdistus lk
                         JOIN toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
                         JOIN lasku l on lk.lasku = l.id
                WHERE lk.maksueratyyppi IN ('kokonaishintainen'::maksueratyyppi, 'lisatyo'::maksueratyyppi) -- Sisältää kustannusarvioituihin (määrämitattaviin) töihin sekä hallintokorvauksiin kohdistetut kulut ja lisätyökulut.
                  AND lk.poistettu IS NOT TRUE
                  AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm

                LOOP
                    -- Indeksi ei käytössä, annetaan summa sellaisenaan ja merkitään korotus nollaksi
                    SELECT kohdistetut.summa AS summa,
                           kohdistetut.summa AS korotettuna,
                           0::NUMERIC        AS korotus
                    INTO kohdistettu;

                    RAISE NOTICE 'kohdistettu-rivi: %', kohdistettu;
                    IF kohdistetut.tot_alkanut < aikavali_alkupvm THEN
                        -- jo laskutettu
                        kohdistetut_laskutettu := kohdistetut_laskutettu + COALESCE(kohdistettu.summa, 0.0);
                    ELSE
                        -- laskutetaan nyt
                        kohdistetut_laskutetaan := kohdistetut_laskutetaan + COALESCE(kohdistettu.summa, 0.0);
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
                ELSIF (hoitokauden_suolasakko_rivi.maksukuukausi <
                       (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER)
                    AND (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER) < 10)
                THEN
                    RAISE NOTICE 'Suolasakko on laskutettu aiemmin hoitokaudella kuukautena %', hoitokauden_suolasakko_rivi.maksukuukausi;
                    suolasakot_laskutettu := hoitokauden_laskettu_suolasakko_rivi.summa;
                    suolasakot_laskutettu_ind_korotettuna := hoitokauden_laskettu_suolasakko_rivi.korotettuna;
                    suolasakot_laskutettu_ind_korotus := hoitokauden_laskettu_suolasakko_rivi.korotus;
                    -- Jos valittu yksittäinen kuukausi on maksukuukausi TAI jos kyseessä koko hoitokauden raportti (poikkeustapaus)
                ELSIF (hoitokauden_suolasakko_rivi.maksukuukausi =
                       (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER))
                    OR (aikavali_alkupvm = hk_alkupvm AND aikavali_loppupvm = hk_loppupvm) THEN
                    RAISE NOTICE 'Suolasakko laskutetaan tässä kuussa % tai kyseessä koko hoitokauden LYV-raportti.', hoitokauden_suolasakko_rivi.maksukuukausi;
                    suolasakot_laskutetaan := hoitokauden_laskettu_suolasakko_rivi.summa;
                    suolasakot_laskutetaan_ind_korotettuna := hoitokauden_laskettu_suolasakko_rivi.korotettuna;
                    suolasakot_laskutetaan_ind_korotus := hoitokauden_laskettu_suolasakko_rivi.korotus;
                ELSE
                    RAISE NOTICE 'Suolasakkoa ei vielä laskutettu, maksukuukauden arvo: %', hoitokauden_suolasakko_rivi.maksukuukausi;
                END IF;
            END IF;

            -- Bonukset erotetaan erilliskustannuksista tyypin perusteella
            bonukset_laskutettu := 0.0;
            bonukset_laskutettu_ind_korotettuna := 0.0;
            bonukset_laskutettu_ind_korotus := 0.0;
            bonukset_laskutetaan := 0.0;
            bonukset_laskutetaan_ind_korotettuna := 0.0;
            bonukset_laskutetaan_ind_korotus := 0.0;

            FOR eki IN
                SELECT ek.pvm, ek.rahasumma, ek.indeksin_nimi, ek.tyyppi
                FROM erilliskustannus ek
                WHERE ek.sopimus IN (SELECT id FROM sopimus WHERE urakka = ur)
                  AND ek.toimenpideinstanssi = t.tpi
                  AND ek.pvm >= hk_alkupvm
                  AND ek.pvm <= aikavali_loppupvm
                  AND ek.poistettu IS NOT TRUE
                LOOP
                    IF eki.tyyppi = 'asiakastyytyvaisyysbonus' THEN
                        -- Bonus
                        SELECT *
                        FROM laske_hoitokauden_asiakastyytyvaisyysbonus(ur, eki.pvm, ind, eki.rahasumma)
                        INTO bonukset_rivi;
                        IF eki.pvm < aikavali_alkupvm THEN
                            bonukset_laskutettu := bonukset_laskutettu + COALESCE(bonukset_rivi.summa, 0.0);
                            bonukset_laskutettu_ind_korotettuna :=
                                    bonukset_laskutettu_ind_korotettuna + bonukset_rivi.korotettuna;
                            bonukset_laskutettu_ind_korotus := bonukset_laskutettu_ind_korotus + bonukset_rivi.korotus;
                        ELSE
                            bonukset_laskutetaan := bonukset_laskutetaan + COALESCE(bonukset_rivi.summa, 0.0);
                            bonukset_laskutetaan_ind_korotettuna :=
                                    bonukset_laskutetaan_ind_korotettuna + bonukset_rivi.korotettuna;
                            bonukset_laskutetaan_ind_korotus :=
                                    bonukset_laskutetaan_ind_korotus + bonukset_rivi.korotus;
                        END IF;
                    ELSE
                        -- Muita erilliskustannuksia kuin bonus ei teiden hoidon urakoissa käytetä
                    END IF;
                END LOOP;
            RAISE NOTICE 'Bonuksia laskutettu / laskutetaan: % / %', bonukset_laskutettu, bonukset_laskutetaan;

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


            -- Indeksisummia ei lasketa, indeksit käytössä vain hoidon johdon kustannuksissa. Ei ole muuta yhteenlaskettavaa.

            -- Kustannusten kokonaissummat
            kaikki_paitsi_kohdistetut_laskutettu := 0.0;
            kaikki_laskutettu := 0.0;

            kaikki_paitsi_kohdistetut_laskutetaan := 0.0;
            kaikki_laskutetaan := 0.0;

            kaikki_paitsi_kohdistetut_laskutettu := sakot_laskutettu +
                                                    COALESCE(suolasakot_laskutettu, 0.0) +
                                                    bonukset_laskutettu + kohdistetut_laskutettu;

            kaikki_laskutettu := kaikki_paitsi_kohdistetut_laskutettu + kohdistetut_laskutettu;

            kaikki_paitsi_kohdistetut_laskutetaan := sakot_laskutetaan +
                                                     COALESCE(suolasakot_laskutetaan, 0.0) +
                                                     bonukset_laskutetaan + kohdistetut_laskutetaan;
            kaikki_laskutetaan := kaikki_paitsi_kohdistetut_laskutetaan + kohdistetut_laskutetaan;


            RAISE NOTICE '
    Yhteenveto:';
            RAISE NOTICE 'LASKUTETTU ENNEN AIKAVÄLIÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
            RAISE NOTICE 'hoidonjohto_laskutettu: %', hoidonjohto_laskutettu;
            RAISE NOTICE 'kohdistetut_laskutettu: %', kohdistetut_laskutettu;
            RAISE NOTICE 'Kokonaishintaiset yhteensä (laskutettu): %', (hoidonjohto_laskutettu + kohdistetut_laskutettu);
            RAISE NOTICE 'sakot_laskutettu: %', sakot_laskutettu;
            RAISE NOTICE 'suolasakot_laskutettu: %', suolasakot_laskutettu;
            RAISE NOTICE 'bonukset_laskutettu: %', bonukset_laskutettu;

            RAISE NOTICE 'LASKUTETAAN AIKAVÄLILLÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
            RAISE NOTICE 'hoidonjohto_laskutetaan: %', hoidonjohto_laskutetaan;
            RAISE NOTICE 'kohdistetut_laskutetaan: %', kohdistetut_laskutetaan;
            RAISE NOTICE 'Kokonaishintaiset yhteensä (laskutetaan): %', (hoidonjohto_laskutetaan + kohdistetut_laskutetaan);
            RAISE NOTICE 'sakot_laskutetaan: %', sakot_laskutetaan;
            RAISE NOTICE 'suolasakot_laskutetaan: %', suolasakot_laskutetaan;
            RAISE NOTICE 'bonukset_laskutetaan: %', bonukset_laskutetaan;

            RAISE NOTICE 'kaikki_paitsi_kohdistetut_laskutettu: %', kaikki_paitsi_kohdistetut_laskutettu;
            RAISE NOTICE 'kaikki_laskutettu: %', kaikki_laskutettu;
            RAISE NOTICE 'kaikki_paitsi_kohdistetut_laskutetaan: %', kaikki_paitsi_kohdistetut_laskutetaan;
            RAISE NOTICE 'kaikki_laskutetaan: %', kaikki_laskutetaan;

            RAISE NOTICE 'suolasakko_kaytossa: %', suolasakko_kaytossa;
            RAISE NOTICE 'lampotila_puuttuu: %', lampotila_puuttuu;

            RAISE NOTICE '***** Käsitelly loppui toimenpiteelle: %  *****', t.nimi;

            -- Sovitetaan kerätty laskutusdata vanhaan laskutusyhteenvetorivin muotoon.
            -- Osa tiedoista jää täyttämättä. Vaikka MH-urakoissa on äkillistä hoitotyötä, vahingonkorjauksia ja lisätöitä, niitä ei
            -- käsitellä maksuerissä erillisinä.
            rivi := (t.nimi,
                     t.tuotekoodi,
                     t.tpi,
                     perusluku,
                     NULL, -- kaikki_paitsi_kht_laskutettu_ind_korotus
                     NULL, -- kaikki_laskutettu_ind_korotus
                     NULL, -- kaikki_paitsi_kht_laskutetaan_ind_korotus
                     NULL, -- kaikki_laskutetaan_ind_korotus
                     NULL, -- kaikki_paitsi_kht_laskutettu
                     NULL, -- kaikki_laskutettu
                     NULL, -- kaikki_paitsi_kht_laskutetaan
                     NULL, -- kaikki_laskutetaan
                     (hoidonjohto_laskutettu + kohdistetut_laskutettu), -- kht_laskutettu
                     (hoidonjohto_laskutettu_ind_korotettuna + kohdistetut_laskutettu), -- kht_laskutettu_ind_korotettuna
                     hoidonjohto_laskutettu_ind_korotus, -- kht_laskutettu_ind_korotus
                     (hoidonjohto_laskutetaan + kohdistetut_laskutetaan), -- kht_laskutetaan
                     (hoidonjohto_laskutetaan_ind_korotettuna + kohdistetut_laskutetaan), -- kht_laskutetaan_ind_korotettuna
                     hoidonjohto_laskutetaan_ind_korotus, -- kht_laskutetaan_ind_korotus
                     NULL, -- yht_laskutettu
                     NULL, -- yht_laskutettu_ind_korotettuna
                     NULL, -- yht_laskutettu_ind_korotus
                     NULL, -- yht_laskutetaan
                     NULL, -- yht_laskutetaan_ind_korotettuna
                     NULL, -- yht_laskutetaan_ind_korotus
                     sakot_laskutettu,
                     sakot_laskutettu_ind_korotettuna,
                     sakot_laskutettu_ind_korotus,
                     sakot_laskutetaan,
                     sakot_laskutetaan_ind_korotettuna,
                     sakot_laskutetaan_ind_korotus,
                     suolasakot_laskutettu,
                     suolasakot_laskutettu_ind_korotettuna,
                     suolasakot_laskutettu_ind_korotus,
                     suolasakot_laskutetaan,
                     suolasakot_laskutetaan_ind_korotettuna,
                     suolasakot_laskutetaan_ind_korotus,
                     NULL, -- muutostyot_laskutettu
                     NULL, -- muutostyot_laskutettu_ind_korotettuna
                     NULL, -- muutostyot_laskutettu_ind_korotus
                     NULL, -- muutostyot_laskutetaan
                     NULL, -- muutostyot_laskutetaan_ind_korotettuna
                     NULL, -- muutostyot_laskutetaan_ind_korotus
                     NULL, -- akilliset_hoitotyot_laskutettu
                     NULL, -- akilliset_hoitotyot_laskutettu_ind_korotettuna
                     NULL, -- akilliset_hoitotyot_laskutettu_ind_korotus
                     NULL, -- akilliset_hoitotyot_laskutetaan
                     NULL, -- akilliset_hoitotyot_laskutetaan_ind_korotettuna
                     NULL, -- akilliset_hoitotyot_laskutetaan_ind_korotus
                     NULL, -- erilliskustannukset_laskutettu
                     NULL, -- erilliskustannukset_laskutettu_ind_korotettuna
                     NULL, -- erilliskustannukset_laskutettu_ind_korotus
                     NULL, -- erilliskustannukset_laskutetaan
                     NULL, -- erilliskustannukset_laskutetaan_ind_korotettuna
                     NULL, -- erilliskustannukset_laskutetaan_ind_korotus
                     bonukset_laskutettu,
                     bonukset_laskutettu_ind_korotettuna,
                     bonukset_laskutettu_ind_korotus,
                     bonukset_laskutetaan,
                     bonukset_laskutetaan_ind_korotettuna,
                     bonukset_laskutetaan_ind_korotus,
                     suolasakko_kaytossa,
                     lampotila_puuttuu,
                     NULL, -- vahinkojen_korjaukset_laskutettu
                     NULL, -- vahinkojen_korjaukset_laskutettu_ind_korotettuna
                     NULL, -- vahinkojen_korjaukset_laskutettu_ind_korotus
                     NULL, -- vahinkojen_korjaukset_laskutetaan
                     NULL, -- vahinkojen_korjaukset_laskutetaan_ind_korotettuna
                     NULL -- vahinkojen_korjaukset_laskutetaan_ind_korotus
                );

            cache := cache || rivi;
            RETURN NEXT rivi;

        END LOOP;

    RAISE NOTICE 'tallennetaan cacheen';
    -- Tallenna cacheen ajettu laskutusyhteenveto
    -- Jos indeksit tai urakan toteumat muuttuvat,
    -- pitää niiden transaktioiden
    -- poistaa myös cache
    INSERT
    INTO laskutusyhteenveto_cache (urakka, alkupvm, loppupvm, rivit)
    VALUES (ur, aikavali_alkupvm, aikavali_loppupvm, cache)
    ON CONFLICT ON CONSTRAINT uniikki_urakka_aika DO UPDATE SET rivit = cache, tallennettu = NOW();
END;
$$ LANGUAGE plpgsql;

-- Kun hoidonjohdon työn suunnitelma muuttuu, poista muistetut
CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_kht() RETURNS trigger AS $$
DECLARE
    maksupvm DATE;
    ur INTEGER;
    tpi_id INTEGER;
BEGIN
    IF TG_OP != 'DELETE' THEN
        maksupvm := NEW.maksupvm;
        tpi_id := NEW.toimenpideinstanssi;
    ELSE
        maksupvm := OLD.maksupvm;
        tpi_id := OLD.toimenpideinstanssi;
    END IF;

    IF maksupvm IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT INTO ur urakka
    FROM toimenpideinstanssi tpi
    WHERE tpi.id = tpi_id;

    PERFORM poista_hoitokauden_muistetut_laskutusyht(ur, maksupvm);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER tg_poista_muistetut_laskutusyht_kht
    AFTER INSERT OR UPDATE OR DELETE
    ON kokonaishintainen_tyo
    FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_kht();
