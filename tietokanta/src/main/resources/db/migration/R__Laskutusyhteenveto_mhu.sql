-- MHU-urakoiden laskutusyhteeneveto

-- MHU hoidonjohdon erillishankinnat
CREATE TYPE hjerillishankinnat_rivi
AS
(
    hj_erillishankinnat_laskutettu  NUMERIC,
    hj_erillishankinnat_laskutetaan NUMERIC
);
CREATE OR REPLACE FUNCTION hj_erillishankinnat(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                               toimenpide_koodi TEXT, t_instanssi INTEGER, ur INTEGER)
    RETURNS SETOF hjerillishankinnat_rivi AS
$$
DECLARE

    rivi                                 hjerillishankinnat_rivi;
    hj_erillishankinnat_laskutettu       NUMERIC;
    hj_erillishankinnat_laskutetaan      NUMERIC;
    hj_erillishankinnat_laskutettu_rivi  RECORD;
    hj_erillishankinnat_laskutetaan_rivi RECORD;
    tehtavaryhma_id                      INTEGER;

BEGIN
    -- Haetaan hoidon johdon erillishankinnat

    RAISE NOTICE 'hj_erillishankinnat: toimenpidekoodi %' , toimenpide_koodi;
    tehtavaryhma_id := (SELECT id FROM tehtavaryhma WHERE nimi = 'Erillishankinnat (W)');
    RAISE NOTICE 'hj_erillishankinnat :: tehtavaryhma_i: %', tehtavaryhma_id;

    hj_erillishankinnat_laskutettu := 0.0;
    hj_erillishankinnat_laskutetaan := 0.0;

    IF (toimenpide_koodi = '23150') THEN

        RAISE NOTICE 'hj_erillishankinnat lasketaan mukaan, koska toimenpideinstanssi on hoidon johto. %', t_instanssi;

        -- Ennen tarkasteltavaa aikaväliä laskutetut hoidonjohdon palkkiot - (päätellään tpi:stä ja toimenpidekoodista )
        -- Käydään läpi tiedot taulusta: kustannusarvioitu_tyo

        -- hj_palkkio - laskutettu
        FOR hj_erillishankinnat_laskutettu_rivi IN
            SELECT id,
                   coalesce(summa, 0)                          as erillishankinta_summa,
                   (SELECT (date_trunc('MONTH', format('%s-%s-%s', vuosi, kuukausi, 1)::DATE) +
                            INTERVAL '1 MONTH - 1 day')::DATE) as tot_alkanut

            FROM kustannusarvioitu_tyo kat
            WHERE toimenpideinstanssi = t_instanssi
              AND tehtavaryhma = tehtavaryhma_id
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
                RAISE NOTICE 'Erillishankinnat laskutettu :: summa: %', hj_erillishankinnat_laskutettu_rivi.erillishankinta_summa;
                hj_erillishankinnat_laskutettu :=
                            hj_erillishankinnat_laskutettu + COALESCE(
                                hj_erillishankinnat_laskutettu_rivi.erillishankinta_summa, 0.0);
            END LOOP;


        -- Tarkasteltavalla aikavälillä laskutetut tai laskutettavat erillishankinnat
        -- Käydään läpi tiedot taulusta: kustannusarvioitu_tyo
        -- Kuluvan kuukauden laskutettava summa nousee maksuerään vasta kuukauden viimeisenä päivänä.


        -- erillishankinnat - laskutetaan
        FOR hj_erillishankinnat_laskutetaan_rivi IN
            SELECT id,
                   coalesce(summa, 0)                          as erillishankinta_summa,
                   (SELECT (date_trunc('MONTH', format('%s-%s-%s', vuosi, kuukausi, 1)::DATE) +
                            INTERVAL '1 MONTH - 1 day')::DATE) as tot_alkanut
            FROM kustannusarvioitu_tyo kat
            WHERE toimenpideinstanssi = t_instanssi
              AND tehtavaryhma = tehtavaryhma_id
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
                IF (hj_erillishankinnat_laskutetaan_rivi.tot_alkanut::DATE <= current_date) THEN
                    hj_erillishankinnat_laskutetaan :=
                                hj_erillishankinnat_laskutetaan +
                                COALESCE(hj_erillishankinnat_laskutetaan_rivi.erillishankinta_summa, 0.0);
                END IF;
            END LOOP;

    END IF; -- tuotekoodi = 23150 (Hoidonjohto)

    rivi := (hj_erillishankinnat_laskutettu, hj_erillishankinnat_laskutetaan);
    RETURN NEXT rivi;
END;
$$ LANGUAGE plpgsql;


-- MHU hoidonjohdon palkkio pilkotaan tähän
CREATE TYPE hjpalkkio_rivi
AS
(
    hj_palkkio_laskutettu  NUMERIC,
    hj_palkkio_laskutetaan NUMERIC
);

CREATE OR REPLACE FUNCTION hj_palkkio(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                      toimenpide_koodi TEXT,
                                      t_instanssi INTEGER, ur INTEGER)
    RETURNS SETOF hjpalkkio_rivi AS
$$
DECLARE

    rivi                        hjpalkkio_rivi;
    hj_palkkio_laskutettu       NUMERIC;
    hj_palkkio_laskutetaan      NUMERIC;
    hj_palkkio_laskutettu_rivi  RECORD;
    hj_palkkio_laskutetaan_rivi RECORD;
    tehtavaryhma_id             INTEGER;

BEGIN
    -- Haetaan hoidon johdon yhteenvetoja

    RAISE NOTICE 'hj_palkkio: toimenpidekoodi %' , toimenpide_koodi;
    tehtavaryhma_id := (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoidonjohtopalkkio (G)');

    hj_palkkio_laskutettu := 0.0;
    hj_palkkio_laskutetaan := 0.0;

    IF (toimenpide_koodi = '23150') THEN

        RAISE NOTICE 'hj_palkkio lasketaan mukaan, koska toimenpideinstanssi on hoidon johto. %', t_instanssi;

        -- Ennen tarkasteltavaa aikaväliä laskutetut hoidonjohdon palkkiot - (päätellään tpi:stä ja toimenpidekoodista )
        -- Käydään läpi tiedot taulusta: kustannusarvioitu_tyo

        -- hj_palkkio - laskutettu
        FOR hj_palkkio_laskutettu_rivi IN
            SELECT id,
                   coalesce(summa, 0)                          as hjpalkkio_summa,
                   (SELECT (date_trunc('MONTH', format('%s-%s-%s', vuosi, kuukausi, 1)::DATE) +
                            INTERVAL '1 MONTH - 1 day')::DATE) as tot_alkanut

            FROM kustannusarvioitu_tyo kat
            WHERE toimenpideinstanssi = t_instanssi
              AND tehtavaryhma = tehtavaryhma_id
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
                RAISE NOTICE 'HJ-palkkio laskutettu :: summa: %', hj_palkkio_laskutettu_rivi.hjpalkkio_summa;
                hj_palkkio_laskutettu :=
                            hj_palkkio_laskutettu + COALESCE(hj_palkkio_laskutettu_rivi.hjpalkkio_summa, 0.0);
            END LOOP;


        -- Tarkasteltavalla aikavälillä laskutetut tai laskutettavat hoidonjohdon kustannukset
        -- Käydään läpi tiedot taulusta: kustannusarvioitu_tyo
        -- Kuluvan kuukauden laskutettava summa nousee maksuerään vasta kuukauden viimeisenä päivänä.


        -- hj_palkkio - laskutetaan
        FOR hj_palkkio_laskutetaan_rivi IN
            SELECT id,
                   coalesce(summa, 0)                          as hjpalkkio_summa,
                   (SELECT (date_trunc('MONTH', format('%s-%s-%s', vuosi, kuukausi, 1)::DATE) +
                            INTERVAL '1 MONTH - 1 day')::DATE) as tot_alkanut
            FROM kustannusarvioitu_tyo kat
            WHERE toimenpideinstanssi = t_instanssi
              AND tehtavaryhma = tehtavaryhma_id
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
                IF (hj_palkkio_laskutetaan_rivi.tot_alkanut::DATE <= current_date) THEN
                    hj_palkkio_laskutetaan :=
                                hj_palkkio_laskutetaan + COALESCE(hj_palkkio_laskutetaan_rivi.hjpalkkio_summa, 0.0);
                END IF;
            END LOOP;

    END IF; -- tuotekoodi = 23150 (Hoidonjohto)

    rivi := (hj_palkkio_laskutettu, hj_palkkio_laskutetaan);
    RETURN NEXT rivi;
END;
$$ LANGUAGE plpgsql;


-- MHU hoidon johto on niin iso ja monimutkainen laskenta, että se on eriytetty tähän
CREATE TYPE hoidonjohto_rivi
AS
(
    hoidonjohto_laskutettu  NUMERIC,
    hoidonjohto_laskutetaan NUMERIC
);

CREATE OR REPLACE FUNCTION hoidon_johto_yhteenveto(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                                   toimenpide_koodi TEXT, t_instanssi INTEGER, ur INTEGER,
                                                   perusluku NUMERIC, indeksinimi VARCHAR, indeksivuosi INTEGER,
                                                   indeksikuukausi INTEGER)
    RETURNS SETOF hoidonjohto_rivi AS
$$
DECLARE

    rivi                     hoidonjohto_rivi;
    hoidonjohto_laskutettu   NUMERIC;
    hoidonjohto_laskutetaan  NUMERIC;
    hoidonjohtoi_laskutettu  RECORD;
    hoidonjohtoi_laskutetaan RECORD;

BEGIN
    -- Haetaan hoidon johdon yhteenvetoja

    RAISE NOTICE 'hoidon_johto_yhteenveto: toimenpidekoodi %' , toimenpide_koodi;
    hoidonjohto_laskutettu := 0.0;
    hoidonjohto_laskutetaan := 0.0;

    IF (toimenpide_koodi = '23150') THEN

        RAISE NOTICE 'Hoidonjohdon kustannuket lasketaan mukaan, koska toimenpideinstanssi on hoidon johto. %', t_instanssi;

        -- Ennen tarkasteltavaa aikaväliä ja aikavälillä laskutetut hoidonjohdon kustannukset
        -- Käytetään taulua: johto_ja_hallintokorvaus

        -- johto_ja_hallintokorvaus - laskutettu
        FOR hoidonjohtoi_laskutettu IN
            SELECT id,
                   (SELECT (date_trunc('MONTH', format('%s-%s-%s', vuosi, kuukausi, 1)::DATE) +
                            INTERVAL '1 MONTH - 1 day')::DATE)      as tot_alkanut,
                   (SELECT korotettuna
                    FROM laske_kuukauden_indeksikorotus(indeksivuosi, indeksikuukausi, indeksinimi,
                                                        coalesce(jhk.tunnit, 0) * coalesce(jhk.tuntipalkka, 0),
                                                        perusluku)) AS korotettuna
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
                hoidonjohto_laskutettu := hoidonjohto_laskutettu + COALESCE(hoidonjohtoi_laskutettu.korotettuna, 0.0);
            END LOOP;

        -- Tarkasteltavalla aikavälillä laskutetut tai laskutettavat hoidonjohdon kustannukset
        -- Käydään läpi tiedot taulusta johto_ja_hallintokorvaus
        -- Kuluvan kuukauden laskutettava summa nousee maksuerään vasta kuukauden viimeisenä päivänä.

        hoidonjohto_laskutetaan := 0.0;

        -- johto_ja_hallintokorvaus - laskutetaan
        FOR hoidonjohtoi_laskutetaan IN
            SELECT id,
                   kuukausi,
                   vuosi,
                   (SELECT (date_trunc('MONTH', format('%s-%s-%s', vuosi, kuukausi, 1)::DATE) +
                            INTERVAL '1 MONTH - 1 day')::DATE)      as tot_alkanut,
                   (SELECT korotettuna
                    FROM laske_kuukauden_indeksikorotus(indeksivuosi, indeksikuukausi, indeksinimi,
                                                        coalesce(jhk.tunnit, 0) * coalesce(jhk.tuntipalkka, 0),
                                                        perusluku)) AS korotettuna
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
                                hoidonjohto_laskutetaan + COALESCE(hoidonjohtoi_laskutetaan.korotettuna, 0.0);

                END IF;
            END LOOP;

    END IF; -- tuotekoodi = 23150 (Hoidonjohto)

    rivi := (hoidonjohto_laskutettu, hoidonjohto_laskutetaan);
    RETURN NEXT rivi;
END;
$$ LANGUAGE plpgsql;



DROP FUNCTION IF EXISTS laskutusyhteenveto_teiden_hoito(hk_alkupvm date, hk_loppupvm date, aikavali_alkupvm date,
    aikavali_loppupvm date, ur integer);

DROP TYPE IF EXISTS laskutusyhteenveto_raportti_mhu_rivi;

CREATE TYPE laskutusyhteenveto_raportti_mhu_rivi
AS
(
    nimi                            VARCHAR,
    tuotekoodi                      VARCHAR,
    tpi                             INTEGER,
    perusluku                       NUMERIC,
    kaikki_laskutettu               NUMERIC,
    kaikki_laskutetaan              NUMERIC,
    tavoitehintaiset_laskutettu     NUMERIC,
    tavoitehintaiset_laskutetaan    NUMERIC,
    lisatyot_laskutettu             NUMERIC,
    lisatyot_laskutetaan            NUMERIC,
    hankinnat_laskutettu            NUMERIC,
    hankinnat_laskutetaan           NUMERIC,
    sakot_laskutettu                NUMERIC,
    sakot_laskutetaan               NUMERIC,
    suolasakot_laskutettu           NUMERIC,
    suolasakot_laskutetaan          NUMERIC,
    -- Hoidon johto
    hoidonjohto_laskutettu          NUMERIC,
    hoidonjohto_laskutetaan         NUMERIC,
    bonukset_laskutettu             NUMERIC,
    bonukset_laskutetaan            NUMERIC,
    erilliskustannukset_laskutettu  NUMERIC,
    erilliskustannukset_laskutetaan NUMERIC,
    hj_palkkio_laskutettu           NUMERIC,
    hj_palkkio_laskutetaan          NUMERIC,
    hj_erillishankinnat_laskutettu  NUMERIC,
    hj_erillishankinnat_laskutetaan NUMERIC,
    suolasakko_kaytossa             BOOLEAN,
    lampotila_puuttuu               BOOLEAN
);

-- Palauttaa maksuerien kokonaissummat
-- Tallentaa laskutusyhteenvetoon päivittyneen tilanteen
CREATE OR REPLACE FUNCTION laskutusyhteenveto_teiden_hoito(hk_alkupvm date, hk_loppupvm date,
                                                           aikavali_alkupvm date, aikavali_loppupvm date, ur integer)
    returns SETOF laskutusyhteenveto_raportti_mhu_rivi
    language plpgsql
as
$$
DECLARE
    t                                     RECORD;
    kaikki_laskutettu                     NUMERIC;
    kaikki_laskutetaan                    NUMERIC;
    tavoitehintaiset_laskutettu           NUMERIC;
    tavoitehintaiset_laskutetaan          NUMERIC;
    lisatyot_laskutettu                   NUMERIC;
    lisatyot_laskutetaan                  NUMERIC;
    lisatyot_rivi                         RECORD;
    lisatyo                               RECORD;
    hankinnat_laskutettu                  NUMERIC;
    hankinnat_laskutetaan                 NUMERIC;
    hankinnat_i                           RECORD;
    hankinnat_rivi                        RECORD;

    -- Sakot
    sakot_laskutettu                      NUMERIC;
    sakot_laskutetaan                     NUMERIC;
    sanktiorivi                           RECORD;
    suolasakot_laskutettu                 NUMERIC;
    suolasakot_laskutetaan                NUMERIC;
    hoitokauden_suolasakko_rivi           RECORD;
    hoitokauden_laskettu_suolasakon_maara NUMERIC;

    -- Hoidon johto
    h_rivi                                RECORD;
    hj_palkkio_rivi                       RECORD;
    -- Hoidon johto :: Bonukset
    alihank_bon_laskutettu                NUMERIC;
    alihank_bon_laskutetaan               NUMERIC;
    lupaus_bon_laskutettu                 NUMERIC;
    lupaus_bon_laskutetaan                NUMERIC;
    lupaus_bon_rivi                       RECORD;
    tavoitepalkk_bon_laskutettu           NUMERIC;
    tavoitepalkk_bon_laskutetaan          NUMERIC;
    asiakas_tyyt_bon_laskutettu           NUMERIC;
    asiakas_tyyt_bon_laskutetaan          NUMERIC;
    asiakas_tyyt_bon_rivi                 RECORD;
    bonukset_laskutettu                   NUMERIC;
    bonukset_laskutetaan                  NUMERIC;

    -- Hoidon johto :: Erilliskustannukset
    erilliskustannukset_laskutettu        NUMERIC;
    erilliskustannukset_laskutetaan       NUMERIC;
    erilliskustannukset_rivi              RECORD;
    erilliskustannus_rivi                 RECORD;
    hoidonjohto_laskutettu                NUMERIC;
    hoidonjohto_laskutetaan               NUMERIC;
    -- Hoidonjohto - HJ-palkkio
    hj_palkkio_laskutettu                 NUMERIC;
    hj_palkkio_laskutetaan                NUMERIC;
    -- Hoidonjohto - Erillihankinnat
    hj_erillishankinnat_laskutettu        NUMERIC;
    hj_erillishankinnat_laskutetaan       NUMERIC;
    hj_erillishankinnat_rivi              RECORD;

    -- Asetuksia
    suolasakko_kaytossa                   BOOLEAN;
    lampotilat_rivi                       RECORD;
    lampotila_puuttuu                     BOOLEAN;
    rivi                                  laskutusyhteenveto_raportti_mhu_rivi;
    aikavali_kuukausi                     NUMERIC;
    aikavali_vuosi                        NUMERIC;
    hk_alkuvuosi                          NUMERIC;
    hk_alkukuukausi                       NUMERIC;
    indeksi_vuosi                         INTEGER;
    indeksi_kuukausi                      INTEGER;
    perusluku                             NUMERIC; -- urakan indeksilaskennan perusluku (urakkasopimusta edeltävän vuoden syys-,loka, marraskuun keskiarvo)
    indeksinimi                           VARCHAR; -- MAKU2015

BEGIN

    -- Hoitokauden alkukuukauteen perustuvaa indeksi käytetään kuluissa, joita urakoitsija ei itse ole syöttänyt, kuten bonuksissa ja sanktioissa.
    -- Muuten indeksiä ei käytetä
    perusluku := indeksilaskennan_perusluku(ur);
    RAISE NOTICE 'PERUSLUKU: %',perusluku;
    indeksinimi := (SELECT indeksi FROM urakka u WHERE u.id = ur);

    aikavali_kuukausi := (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER);
    aikavali_vuosi := (SELECT EXTRACT(YEAR FROM aikavali_alkupvm) :: INTEGER);
    hk_alkuvuosi := (SELECT EXTRACT(YEAR FROM hk_alkupvm) :: INTEGER);
    hk_alkukuukausi := (SELECT EXTRACT(MONTH FROM hk_alkupvm) :: INTEGER);
    indeksi_vuosi := hk_alkuvuosi; -- Hoitokautta edeltävä syyskuu. Eli hoitokausi alkaa aina lokakuussa, niin se on se sama alkuvuosi. Esim. hoitourakka alkaa 2019 -> indeksi_vuosi on 2019
    indeksi_kuukausi := 9;
    -- Aina syyskuu MHU urakoissa. Indeksi otetaan siis aina edellisen vuoden syyskuusta.


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
            RAISE NOTICE '*************************************************************** Laskutusyhteenvedon laskenta alkaa toimenpiteelle: % , ID % ***************************************************************', t.nimi, t.tpi;
            hankinnat_laskutettu := 0.0;


            -- Hoitokaudella ennen aikaväliä ja aikavälillä laskutetut lisätyöt
            lisatyot_laskutettu := 0.0;
            lisatyot_laskutetaan := 0.0;

            FOR lisatyot_rivi IN
                SELECT summa      as lisatyot_summa,
                       l.erapaiva AS erapaiva
                FROM lasku l
                         JOIN lasku_kohdistus lk ON lk.lasku = l.id
                         JOIN toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
                WHERE lk.maksueratyyppi = 'lisatyo' -- TODO: Placeholder. Tällaista maksuerätyyppiä ei ole. Kiinteähintaiset lähetetään kokonaishintaisessa maksueraässä.
                  AND lk.poistettu IS NOT TRUE
                  AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm

                LOOP

                    SELECT lisatyot_rivi.lisatyot_summa AS summa,
                           0::NUMERIC                   as korotus
                    INTO lisatyo;

                    RAISE NOTICE 'lisatyot_rivi: %', lisatyo;
                    IF lisatyot_rivi.erapaiva <= aikavali_loppupvm THEN
                        -- Hoitokauden alusta
                        lisatyot_laskutettu := lisatyot_laskutettu + COALESCE(lisatyo.summa, 0.0);

                        IF lisatyot_rivi.erapaiva >= aikavali_alkupvm AND
                           lisatyot_rivi.erapaiva <= aikavali_loppupvm THEN
                            -- Laskutetaan nyt
                            lisatyot_laskutetaan := lisatyot_laskutetaan + COALESCE(lisatyo.summa, 0.0);
                        END IF;
                    END IF;
                    RAISE NOTICE 'lisatyo: %', lisatyo;
                END LOOP;


            -- Hoitokaudella ennen aikaväliä ja aikavälillä laskutetut hankinnat työt
            hankinnat_laskutettu := 0.0;
            hankinnat_laskutetaan := 0.0;

            FOR hankinnat_i IN
                SELECT summa      as kht_summa,
                       l.erapaiva AS erapaiva
                FROM lasku l
                         JOIN lasku_kohdistus lk ON lk.lasku = l.id
                         JOIN toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
                WHERE lk.maksueratyyppi = 'kokonaishintainen' -- TODO: Sisältää kiinteähintaiset, kustannusarvioidut ja yksikkohintaiset työt
                  AND lk.poistettu IS NOT TRUE
                  AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm

                LOOP

                    SELECT hankinnat_i.kht_summa AS summa,
                           hankinnat_i.kht_summa AS korotettuna,
                           0::NUMERIC            as korotus
                    INTO hankinnat_rivi;

                    RAISE NOTICE 'hankinnat_rivi: % TPI %', hankinnat_rivi, t.tpi;
                    RAISE NOTICE 'hankinnat_rivi.summa: % TPI %', hankinnat_rivi.summa, t.tpi;

                    IF hankinnat_i.erapaiva <= aikavali_loppupvm THEN
                        -- Hoitokauden alusta
                        hankinnat_laskutettu :=
                                hankinnat_laskutettu + COALESCE(hankinnat_rivi.summa, 0.0);

                        IF hankinnat_i.erapaiva >= aikavali_alkupvm AND
                           hankinnat_i.erapaiva <= aikavali_loppupvm THEN
                            -- Laskutetaan nyt
                            hankinnat_laskutetaan :=
                                    hankinnat_laskutetaan + COALESCE(hankinnat_rivi.summa, 0.0);
                        END IF;
                    END IF;

                    RAISE NOTICE 'hankinnat_laskutettu: %', hankinnat_laskutettu;
                    RAISE NOTICE 'hankinnat_laskutetaan: %', hankinnat_laskutetaan;
                END LOOP;

            -- SANKTIOT
            -- Hoitokaudella ennen aikaväliä ja aikavaälillä laskutetut sanktiot
            -- Sanktioihin lasketaan indeksikorotukset matkaan hoitokauden ensimmäisen kuukauden indeksiarvolla
            sakot_laskutettu := 0.0;
            sakot_laskutetaan := 0.0;

            FOR sanktiorivi IN SELECT -maara                                                   AS maara,
                                      perintapvm,
                                      indeksi,
                                      perintapvm,
                                      (SELECT korotettuna
                                       FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi, indeksinimi,
                                                                           -maara, perusluku)) AS indeksikorotettuna
                               FROM sanktio s
                               WHERE s.toimenpideinstanssi = t.tpi
                                 AND s.maara IS NOT NULL
                                 AND s.perintapvm >= hk_alkupvm
                                 AND s.perintapvm <= aikavali_loppupvm
                                 AND s.poistettu IS NOT TRUE
                LOOP

                    IF sanktiorivi.perintapvm <= aikavali_loppupvm THEN
                        -- Hoitokauden alusta
                        RAISE NOTICE 'sanktiorivi :: Määrä: %', sanktiorivi.maara;
                        RAISE NOTICE 'sanktiorivi :: indeksikorotettuna: % ', sanktiorivi.indeksikorotettuna;

                        sakot_laskutettu := sakot_laskutettu + COALESCE(sanktiorivi.indeksikorotettuna, 0.0);

                        IF sanktiorivi.perintapvm >= aikavali_alkupvm AND
                           sanktiorivi.perintapvm <= aikavali_loppupvm THEN
                            -- Laskutetaan nyt
                            sakot_laskutetaan := sakot_laskutetaan + COALESCE(sanktiorivi.indeksikorotettuna, 0.0);
                        END IF;
                    END IF;

                END LOOP;

            -- Onko suolasakko käytössä urakassa
            IF (SELECT count(*)
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
            SELECT l.*
            FROM "lampotilat" l
            WHERE l.urakka = ur
              AND l.alkupvm = hk_alkupvm
              AND l.loppupvm = hk_loppupvm
            INTO lampotilat_rivi;

            RAISE NOTICE 'Urakalle % Lämpötilat: % ',ur, lampotilat_rivi;

            IF (lampotilat_rivi IS NULL OR lampotilat_rivi.keskilampotila IS NULL OR
                lampotilat_rivi.pitka_keskilampotila IS NULL)
            THEN
                RAISE NOTICE 'Urakalle % ei ole lämpötiloja hoitokaudelle % - %', ur, hk_alkupvm, hk_loppupvm;
                RAISE NOTICE 'Keskilämpötila hoitokaudella %, pitkän ajan keskilämpötila %', lampotilat_rivi.keskilampotila, lampotilat_rivi.pitka_keskilampotila;
                lampotila_puuttuu = TRUE;
            ELSE
                lampotila_puuttuu = FALSE;
            END IF;


            suolasakot_laskutettu := 0.0;
            suolasakot_laskutetaan := 0.0;

            -- Suolasakko lasketaan vain Talvihoito-toimenpiteelle (tuotekoodi '23100')
            IF t.tuotekoodi = '23100' THEN
                -- TODO: Tein tästä loopin, koska ajattelin, että suolasakkoja voidaan antaa yhdelle talvelle monta, mutta ilmeisesti ei pysty?
                FOR hoitokauden_suolasakko_rivi IN
                    SELECT * FROM suolasakko s WHERE s.urakka = ur AND hk_alkuvuosi = s.hoitokauden_alkuvuosi
                    LOOP

                        RAISE NOTICE 'hoitokauden_suolasakko_rivi :: % ',hoitokauden_suolasakko_rivi;

                        hoitokauden_laskettu_suolasakon_maara :=
                                    (SELECT hoitokauden_suolasakko(ur, hk_alkupvm, hk_loppupvm));
                        RAISE NOTICE 'hoitokauden_laskettu_suolasakon_maara: %', hoitokauden_laskettu_suolasakon_maara;
                        -- Lasketaan suolasakolle indeksikorotus
                        hoitokauden_laskettu_suolasakon_maara := (SELECT korotettuna
                                                                  FROM laske_kuukauden_indeksikorotus(
                                                                          indeksi_vuosi,
                                                                          indeksi_kuukausi, indeksinimi,
                                                                          hoitokauden_laskettu_suolasakon_maara,
                                                                          perusluku));
                        RAISE NOTICE 'hoitokauden_laskettu_suolasakon_maara indeksikorotettuna: %', hoitokauden_laskettu_suolasakon_maara;

                        -- Jos suolasakko ei ole käytössä, ei edetä
                        IF (suolasakko_kaytossa = FALSE) THEN
                            RAISE NOTICE 'Suolasakko ei käytössä annetulla aikavälillä urakassa %, aikavali_alkupvm: %, hoitokauden_suolasakko_rivi: %', ur, aikavali_alkupvm, hoitokauden_suolasakko_rivi;
                            -- Suolasakko voi olla laskutettu jo hoitokaudella vain kk:ina 6-9 koska mahdolliset laskutus-kk:t ovat 5-9
                        ELSIF (hoitokauden_suolasakko_rivi.maksukuukausi < aikavali_kuukausi AND aikavali_kuukausi < 10)
                            OR (hoitokauden_suolasakko_rivi.hoitokauden_alkuvuosi < aikavali_vuosi AND hoitokauden_suolasakko_rivi.maksukuukausi < aikavali_kuukausi) THEN
                            RAISE NOTICE 'Suolasakko (summa: % ) on laskutettu aiemmin hoitokaudella kuukausi: %', hoitokauden_laskettu_suolasakon_maara, hoitokauden_suolasakko_rivi.maksukuukausi;
                            suolasakot_laskutettu := hoitokauden_laskettu_suolasakon_maara;
                            -- Jos valittu yksittäinen kuukausi on maksukuukausi TAI jos kyseessä koko hoitokauden raportti (poikkeustapaus)
                        ELSIF (hoitokauden_suolasakko_rivi.maksukuukausi = aikavali_kuukausi AND
                               hoitokauden_suolasakko_rivi.hoitokauden_alkuvuosi < aikavali_vuosi) THEN
                            RAISE NOTICE 'Suolasakko (summa: %) laskutetaan tässä kuussa: % ',hoitokauden_laskettu_suolasakon_maara, hoitokauden_suolasakko_rivi.maksukuukausi;
                            suolasakot_laskutetaan := hoitokauden_laskettu_suolasakon_maara;
                        ELSE
                            RAISE NOTICE 'Suolasakkoa ei vielä laskutettu, maksukuukauden arvo: %', hoitokauden_suolasakko_rivi.maksukuukausi;
                        END IF;

                    END LOOP;
            END IF;

            -- ERILLISKUSTANNUKSET  hoitokaudella
            -- bonukset lasketaan erikseen tyypin perusteella
            erilliskustannukset_laskutettu := 0.0;
            erilliskustannukset_laskutetaan := 0.0;
            alihank_bon_laskutettu := 0.0;
            alihank_bon_laskutetaan := 0.0;
            lupaus_bon_laskutettu := 0.0;
            lupaus_bon_laskutetaan := 0.0;
            tavoitepalkk_bon_laskutettu := 0.0;
            tavoitepalkk_bon_laskutetaan := 0.0;
            asiakas_tyyt_bon_laskutettu := 0.0;
            asiakas_tyyt_bon_laskutetaan := 0.0;
            bonukset_laskutettu := 0.0;
            bonukset_laskutetaan := 0.0;

            FOR erilliskustannus_rivi IN
                SELECT ek.pvm, ek.rahasumma, ek.indeksin_nimi, ek.tyyppi
                FROM erilliskustannus ek
                WHERE ek.sopimus IN (SELECT id FROM sopimus WHERE urakka = ur)
                  AND ek.toimenpideinstanssi = t.tpi
                  AND ek.pvm >= hk_alkupvm
                  AND ek.pvm <= aikavali_loppupvm
                  AND ek.poistettu IS NOT TRUE
                LOOP

                    RAISE NOTICE ' ********************************************* ERILLISKUSTANNUS Tyyppi = % ', erilliskustannus_rivi.tyyppi;

                    IF erilliskustannus_rivi.tyyppi = 'alihankintabonus' THEN
                        -- Bonus :: alihankintabonus
                        IF erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                            -- Hoitokauden alusta
                            alihank_bon_laskutettu :=
                                        alihank_bon_laskutettu + COALESCE(erilliskustannus_rivi.rahasumma, 0.0);

                            IF erilliskustannus_rivi.pvm >= aikavali_alkupvm AND
                               erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                                -- Laskutetaan nyt
                                alihank_bon_laskutetaan :=
                                            alihank_bon_laskutetaan + COALESCE(erilliskustannus_rivi.rahasumma, 0.0);
                            END IF;
                        END IF;

                    ELSEIF erilliskustannus_rivi.tyyppi = 'lupausbonus' THEN
                        -- Bonus :: lupausbonus
                        SELECT *
                        FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi,
                                                            erilliskustannus_rivi.indeksin_nimi,
                                                            erilliskustannus_rivi.rahasumma, perusluku)
                        INTO lupaus_bon_rivi;

                        IF erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                            -- Hoitokauden alusta
                            lupaus_bon_laskutettu := lupaus_bon_laskutettu + COALESCE(lupaus_bon_rivi.korotettuna, 0.0);

                            IF erilliskustannus_rivi.pvm >= aikavali_alkupvm AND
                               erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                                -- Laskutetaan nyt
                                lupaus_bon_laskutetaan :=
                                        lupaus_bon_laskutetaan + COALESCE(lupaus_bon_rivi.korotettuna, 0.0);

                            END IF;
                        END IF;
                        -- Asiakastyytyväisyysbonus
                    ELSEIF erilliskustannus_rivi.tyyppi = 'tktt-bonus' OR
                           erilliskustannus_rivi.tyyppi = 'asiakastyytyvaisyysbonus' THEN
                        -- Bonus :: tktt-bonus = tienkäytöntutkimus = asiakastyytyväisyysbonus - nämä on sama asia, mutta erheellisesti laitettu kahtena bonuksena
                        SELECT *
                        FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi,
                                                            erilliskustannus_rivi.indeksin_nimi,
                                                            erilliskustannus_rivi.rahasumma, perusluku)
                        INTO asiakas_tyyt_bon_rivi;

                        IF erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                            -- Hoitokauden alusta
                            asiakas_tyyt_bon_laskutettu :=
                                        asiakas_tyyt_bon_laskutettu + COALESCE(asiakas_tyyt_bon_rivi.korotettuna, 0.0);

                            IF erilliskustannus_rivi.pvm >= aikavali_alkupvm AND
                               erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                                -- Laskutetaan nyt
                                asiakas_tyyt_bon_laskutetaan := asiakas_tyyt_bon_laskutetaan +
                                                                COALESCE(asiakas_tyyt_bon_rivi.korotettuna, 0.0);
                            END IF;
                        END IF;

                    ELSEIF erilliskustannus_rivi.tyyppi = 'tavoitepalkkio' THEN
                        -- Bonus :: lupausbonus
                        IF erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                            -- Hoitokauden alusta
                            tavoitepalkk_bon_laskutettu :=
                                        tavoitepalkk_bon_laskutettu + COALESCE(erilliskustannus_rivi.rahasumma, 0.0);

                            IF erilliskustannus_rivi.pvm >= aikavali_alkupvm AND
                               erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                                -- Laskutetaan nyt
                                tavoitepalkk_bon_laskutetaan :=
                                            tavoitepalkk_bon_laskutetaan +
                                            COALESCE(erilliskustannus_rivi.rahasumma, 0.0);
                            END IF;
                        END IF;

                        -- TODO: Kuuluuko muu - näihin erilliskustannuksiin? Se ei ole siis joukossa: Alihankintabonus, lupausbonus, tktt-bonus/asiakastyytyväisyys tai tavoitepalkkio.
                    ELSIF erilliskustannus_rivi.tyyppi = 'muu' THEN
                        -- Bonus :: muu
                        SELECT *
                        FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi,
                                                            erilliskustannus_rivi.indeksin_nimi,
                                                            erilliskustannus_rivi.rahasumma, perusluku)
                        INTO erilliskustannukset_rivi;

                        IF erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                            -- Hoitokauden alusta
                            erilliskustannukset_laskutettu := erilliskustannukset_laskutettu +
                                                              COALESCE(erilliskustannukset_rivi.korotettuna, 0.0);
                            IF erilliskustannus_rivi.pvm >= aikavali_alkupvm AND
                               erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                                -- Laskutetaan nyt
                                erilliskustannukset_laskutetaan := erilliskustannukset_laskutetaan +
                                                                   COALESCE(erilliskustannukset_rivi.korotettuna, 0.0);
                            END IF;
                        END IF;

                    END IF;
                END LOOP;
            RAISE NOTICE 'Alihankintabonus laskutettu :: laskutetaan: % :: %', alihank_bon_laskutettu, alihank_bon_laskutetaan;
            RAISE NOTICE 'Lupausbonus laskutettu :: laskutetaan: % :: %', lupaus_bon_laskutettu, lupaus_bon_laskutetaan;
            RAISE NOTICE 'Asiakastyytyväisyysbonus laskutettu :: laskutetaan: % :: %', asiakas_tyyt_bon_laskutettu, asiakas_tyyt_bon_laskutetaan;
            RAISE NOTICE 'Tavoitepalkkio laskutettu :: laskutetaan: % :: %', tavoitepalkk_bon_laskutettu, tavoitepalkk_bon_laskutetaan;
            RAISE NOTICE 'Erilliskustannuksia laskutettu :: laskutetaan: % :: %', erilliskustannukset_laskutettu, erilliskustannukset_laskutetaan;

            bonukset_laskutettu :=
                        bonukset_laskutettu + alihank_bon_laskutettu + lupaus_bon_laskutettu +
                        asiakas_tyyt_bon_laskutettu + tavoitepalkk_bon_laskutettu;
            bonukset_laskutetaan :=
                        bonukset_laskutetaan + alihank_bon_laskutetaan + lupaus_bon_laskutetaan +
                        asiakas_tyyt_bon_laskutetaan + tavoitepalkk_bon_laskutetaan;
            RAISE NOTICE 'Bonuksia laskutettu :: laskutetaan: % :: %', bonukset_laskutettu, bonukset_laskutetaan;

            -- HOIDON JOHTO, tpk 23150.
            -- Hoidon johdon kustannukset eli johto- ja hallintokorvays, erillishankinnat ja hoidonjohtopalkkio lasketaan maksuerään suoraan
            -- kustannussuunnitelmasta. Suunniteltu rahasumma siirtyy maksuerään kuukauden viimeisenä päivänä.
            -- Poikkeustapauksissa hoidon johdon kustannuksia kirjataan kulujen kohdistuksessa. Tällöin kustannukset lasketaan mukaan samaan tapaan kuin
            -- muutkin hankinnat (ks. kohdistetut_laskutetaan alla).
            hoidonjohto_laskutettu := 0.0;
            hoidonjohto_laskutetaan := 0.0;
            h_rivi := (select hoidon_johto_yhteenveto(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm, t.tuotekoodi,
                                                      t.tpi, ur, perusluku, indeksinimi, indeksi_vuosi,
                                                      indeksi_kuukausi));

            hoidonjohto_laskutettu := h_rivi.hoidonjohto_laskutettu;
            hoidonjohto_laskutetaan := h_rivi.hoidonjohto_laskutetaan;

            -- HOIDONJOHTO --  HJ-Palkkio
            hj_palkkio_laskutettu := 0.0;
            hj_palkkio_laskutetaan := 0.0;
            hj_palkkio_rivi := (select hj_palkkio(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm,
                                                  t.tuotekoodi,
                                                  t.tpi, ur));
            hj_palkkio_laskutettu := hj_palkkio_rivi.hj_palkkio_laskutettu;
            hj_palkkio_laskutetaan := hj_palkkio_rivi.hj_palkkio_laskutetaan;

            -- HOIDONJOHTO --  erillishankinnat
            hj_erillishankinnat_laskutettu := 0.0;
            hj_erillishankinnat_laskutetaan := 0.0;
            hj_erillishankinnat_rivi := (select hj_erillishankinnat(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm,
                                                                    t.tuotekoodi,
                                                                    t.tpi, ur));
            hj_erillishankinnat_laskutettu := hj_erillishankinnat_rivi.hj_erillishankinnat_laskutettu;
            hj_erillishankinnat_laskutetaan := hj_erillishankinnat_rivi.hj_erillishankinnat_laskutetaan;


            -- Kustannusten kokonaissummat
            kaikki_laskutettu := 0.0;
            kaikki_laskutetaan := 0.0;
            kaikki_laskutettu := sakot_laskutettu +
                                 COALESCE(suolasakot_laskutettu, 0.0) +
                                 bonukset_laskutettu + hankinnat_laskutettu + lisatyot_laskutettu +
                                 hoidonjohto_laskutettu +
                                 erilliskustannukset_laskutettu + hj_palkkio_laskutettu +
                                 hj_erillishankinnat_laskutettu;

            kaikki_laskutetaan := sakot_laskutetaan +
                                  COALESCE(suolasakot_laskutetaan, 0.0) +
                                  bonukset_laskutetaan + hankinnat_laskutetaan + lisatyot_laskutetaan +
                                  hoidonjohto_laskutetaan +
                                  erilliskustannukset_laskutetaan + hj_palkkio_laskutetaan +
                                  hj_erillishankinnat_laskutetaan;

            -- Tavoitehintaan sisältyy: Hankinnat, Johto- ja Hallintokorvaukset, (hoidonjohto tässä), Erillishankinnat, HJ-Palkkio.
            -- Tavoitehintaan ei sisälly: Lisätyöt, Sanktiot, Suolasanktiot, Bonukset
            tavoitehintaiset_laskutettu :=
                        hankinnat_laskutettu + hoidonjohto_laskutettu + hj_erillishankinnat_laskutettu +
                        hj_palkkio_laskutettu;

            tavoitehintaiset_laskutetaan :=
                        hankinnat_laskutetaan + hoidonjohto_laskutetaan + hj_erillishankinnat_laskutetaan +
                        hj_palkkio_laskutetaan;


            RAISE NOTICE '
    Yhteenveto:';
            RAISE NOTICE 'LASKUTETTU ENNEN AIKAVÄLIÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
            RAISE NOTICE 'Lisatyot laskutettu: %', lisatyot_laskutettu;
            RAISE NOTICE 'Hankinnat laskutettu: %', hankinnat_laskutettu;
            RAISE NOTICE 'Sakot laskutettu: %', sakot_laskutettu;
            RAISE NOTICE 'Suolasakot laskutettu: %', suolasakot_laskutettu;
            RAISE NOTICE 'Hoidonjohto laskutettu: %', hoidonjohto_laskutettu;
            RAISE NOTICE 'Bonukset laskutettu: %', bonukset_laskutettu;
            RAISE NOTICE 'Erilliskustannukset laskutettu: %', erilliskustannukset_laskutettu;
            RAISE NOTICE 'HJ-Palkkio laskutettu: %', hj_palkkio_laskutettu;
            RAISE NOTICE 'Erillishankinnat laskutettu: %', hj_erillishankinnat_laskutettu;

            RAISE NOTICE '
    LASKUTETAAN AIKAVÄLILLÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
            RAISE NOTICE 'Lisatyot laskutetaan: %', lisatyot_laskutetaan;
            RAISE NOTICE 'Hankinnat laskutetaan: %', hankinnat_laskutetaan;
            RAISE NOTICE 'Sakot laskutetaan: %', sakot_laskutetaan;
            RAISE NOTICE 'Suolasakot laskutetaan: %', suolasakot_laskutetaan;


            RAISE NOTICE 'Hoidonjohto laskutetaan: %', hoidonjohto_laskutetaan;
            RAISE NOTICE 'Bonukset laskutetaan: %', bonukset_laskutetaan;
            RAISE NOTICE 'Erillisekustannukset laskutetaan: %', erilliskustannukset_laskutetaan;
            RAISE NOTICE 'HJ-Palkkio laskutetaan: %', hj_palkkio_laskutetaan;
            RAISE NOTICE 'Erillishankinnat laskutetaan: %', hj_erillishankinnat_laskutetaan;

            RAISE NOTICE 'Kaikki laskutettu: %', kaikki_laskutettu;
            RAISE NOTICE 'Kaikki laskutetaan: %', kaikki_laskutetaan;

            RAISE NOTICE 'Tavoitehintaiset laskutettu: %', tavoitehintaiset_laskutettu;
            RAISE NOTICE 'Tavoitehintaiset laskutetaan: %', tavoitehintaiset_laskutetaan;

            RAISE NOTICE 'Suolasakko käytössä: %', suolasakko_kaytossa;
            RAISE NOTICE 'Läpmötila puuttuu: %', lampotila_puuttuu;

            RAISE NOTICE '********************************** Käsitelly loppui toimenpiteelle: %  *************************************

    ', t.nimi;

            rivi := (t.nimi, t.tuotekoodi, t.tpi, perusluku,
                     kaikki_laskutettu, kaikki_laskutetaan,
                     tavoitehintaiset_laskutettu, tavoitehintaiset_laskutetaan,
                     lisatyot_laskutettu, lisatyot_laskutetaan,
                     hankinnat_laskutettu, hankinnat_laskutetaan,
                     sakot_laskutettu, sakot_laskutetaan,
                     suolasakot_laskutettu, suolasakot_laskutetaan,
                     hoidonjohto_laskutettu, hoidonjohto_laskutetaan,
                     bonukset_laskutettu, bonukset_laskutetaan,
                     erilliskustannukset_laskutettu, erilliskustannukset_laskutetaan,
                     hj_palkkio_laskutettu, hj_palkkio_laskutetaan,
                     hj_erillishankinnat_laskutettu, hj_erillishankinnat_laskutetaan,
                     suolasakko_kaytossa, lampotila_puuttuu
                );


            RETURN NEXT rivi;
        END LOOP;
END;
$$;

