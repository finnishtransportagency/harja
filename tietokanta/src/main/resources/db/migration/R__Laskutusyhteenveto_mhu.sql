-- ;; TODO: Lisää kustannusarvioitu_tyo ja johto_ja_hallintokorvaus tauluista tuleviin datoihin indeksikorjaukset
-- MHU-urakoiden laskutusyhteeneveto

-- MHU hoidonjohdon erillishankinnat
DROP FUNCTION IF EXISTS hj_erillishankinnat (hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
    toimenpide_koodi TEXT,
    t_instanssi INTEGER,
    urakka_id INTEGER,
    sopimus_id INTEGER, indeksi_vuosi INTEGER, indeksi_kuukausi INTEGER,
    indeksinimi VARCHAR,
    perusluku NUMERIC);
DROP TYPE IF EXISTS HJERILLISHANKINNAT_RIVI;
CREATE TYPE HJERILLISHANKINNAT_RIVI AS
(
    hj_erillishankinnat_laskutettu  NUMERIC,
    hj_erillishankinnat_laskutetaan NUMERIC
);
CREATE OR REPLACE FUNCTION hj_erillishankinnat(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                               toimenpide_koodi TEXT,
                                               t_instanssi INTEGER,
                                               urakka_id INTEGER,
                                               sopimus_id INTEGER, indeksi_vuosi INTEGER, indeksi_kuukausi INTEGER,
                                               indeksinimi VARCHAR,
                                               perusluku NUMERIC) RETURNS SETOF HJERILLISHANKINNAT_RIVI AS
$$
DECLARE

    rivi                            HJERILLISHANKINNAT_RIVI;
    hj_erillishankinnat_laskutettu  NUMERIC;
    hj_erillishankinnat_laskutetaan NUMERIC;
    laskutettu_rivi                 RECORD;
    laskutetaan_rivi                RECORD;
    tehtavaryhma_id                 INTEGER;

BEGIN
    -- Haetaan hoidon johdon erillishankinnat

    RAISE NOTICE 'hj_erillishankinnat: toimenpidekoodi % -- tehtavaryhma_i: % ' , toimenpide_koodi, tehtavaryhma_id;
    tehtavaryhma_id := (SELECT id FROM tehtavaryhma WHERE nimi = 'Erillishankinnat (W)');

    hj_erillishankinnat_laskutettu := 0.0;
    hj_erillishankinnat_laskutetaan := 0.0;

    IF (toimenpide_koodi = '23150') THEN

        RAISE NOTICE 'hj_erillishankinnat lasketaan mukaan, koska toimenpideinstanssi on hoidon johto. %', t_instanssi;

        -- Ennen tarkasteltavaa aikaväliä laskutetut hoidonjohdon erillishankinnat - (päätellään tpi:stä ja toimenpidekoodista )
        -- Käydään läpi tiedot tauluista: kustannusarvioitu_tyo ja lasku_kohdistus
        -- Laskutettu
        FOR laskutettu_rivi IN SELECT (SELECT korotettuna
                                           FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi,
                                                                               indeksinimi, coalesce(kat.summa, 0),
                                                                               perusluku)) AS summa
                                   FROM kustannusarvioitu_tyo kat
                                   WHERE kat.toimenpideinstanssi = t_instanssi
                                     AND kat.tehtavaryhma = tehtavaryhma_id
                                     AND kat.sopimus = sopimus_id
                                     AND (SELECT (date_trunc('MONTH',
                                                             format('%s-%s-%s', kat.vuosi, kat.kuukausi, 1)::DATE))) BETWEEN hk_alkupvm AND aikavali_loppupvm
                               UNION ALL
                               SELECT coalesce(lk.summa, 0) AS summa
                                   FROM lasku l
                                            JOIN lasku_kohdistus lk ON lk.lasku = l.id
                                   WHERE lk.toimenpideinstanssi = t_instanssi
                                     AND lk.poistettu IS NOT TRUE
                                     AND l.urakka = urakka_id
                                     AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
                                     AND lk.tehtavaryhma = tehtavaryhma_id

            LOOP
                RAISE NOTICE 'Erillishankinnat laskutettu :: summa: %', laskutettu_rivi.summa;
                hj_erillishankinnat_laskutettu := hj_erillishankinnat_laskutettu + COALESCE(laskutettu_rivi.summa, 0.0);
            END LOOP;

        -- Tarkasteltavalla aikavälillä laskutettavat erillishankinnat
        -- Käydään läpi tiedot tauluista: kustannusarvioitu_tyo ja lasku_kohdistus
        -- Laskutetaan
        FOR laskutetaan_rivi IN SELECT (SELECT korotettuna
                                            FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi,
                                                                                indeksinimi, coalesce(kat.summa, 0),
                                                                                perusluku)) AS summa
                                    FROM kustannusarvioitu_tyo kat
                                    WHERE kat.toimenpideinstanssi = t_instanssi
                                      AND kat.sopimus = sopimus_id
                                      AND kat.tehtavaryhma = tehtavaryhma_id
                                      AND (SELECT (date_trunc('MONTH',
                                                              format('%s-%s-%s', kat.vuosi, kat.kuukausi, 1)::DATE))) BETWEEN aikavali_alkupvm AND aikavali_loppupvm
                                UNION ALL
                                SELECT coalesce(lk.summa, 0) AS summa
                                    FROM lasku l
                                             JOIN lasku_kohdistus lk ON lk.lasku = l.id
                                    WHERE lk.toimenpideinstanssi = t_instanssi
                                      AND lk.poistettu IS NOT TRUE
                                      AND l.urakka = urakka_id
                                      AND l.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm
                                      AND lk.tehtavaryhma = tehtavaryhma_id
            LOOP
                RAISE NOTICE 'Erillishankinnat laskutetaan :: summa: %', laskutetaan_rivi.summa;
                hj_erillishankinnat_laskutetaan :=
                            hj_erillishankinnat_laskutetaan + COALESCE(laskutetaan_rivi.summa, 0.0);
            END LOOP;
    END IF; -- tuotekoodi = 23150 (Hoidonjohto)

    rivi := (hj_erillishankinnat_laskutettu, hj_erillishankinnat_laskutetaan);
    RETURN NEXT rivi;
END;
$$ LANGUAGE plpgsql;


DROP FUNCTION IF EXISTS hj_palkkio(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                      toimenpide_koodi TEXT,
                                      t_instanssi INTEGER, urakka_id INTEGER,
                                      sopimus_id INTEGER, indeksi_vuosi INTEGER, indeksi_kuukausi INTEGER,
                                      indeksinimi VARCHAR,
                                      perusluku NUMERIC);

-- MHU hoidonjohdon palkkio pilkotaan tähän
DROP TYPE IF EXISTS HJPALKKIO_RIVI;
CREATE TYPE HJPALKKIO_RIVI AS
(
    hj_palkkio_laskutettu  NUMERIC,
    hj_palkkio_laskutetaan NUMERIC
);

CREATE OR REPLACE FUNCTION hj_palkkio(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                      toimenpide_koodi TEXT,
                                      t_instanssi INTEGER, urakka_id INTEGER,
                                      sopimus_id INTEGER, indeksi_vuosi INTEGER, indeksi_kuukausi INTEGER,
                                      indeksinimi VARCHAR,
                                      perusluku NUMERIC) RETURNS SETOF HJPALKKIO_RIVI AS
$$
DECLARE

    rivi                        HJPALKKIO_RIVI;
    hj_palkkio_laskutettu       NUMERIC;
    hj_palkkio_laskutetaan      NUMERIC;
    laskutettu_rivi             RECORD;
    hj_palkkio_laskutetaan_rivi RECORD;
    tehtavaryhma_id             INTEGER;
    toimenpidekoodi_id          INTEGER;

BEGIN
    -- Haetaan hoidon johdon palkkiot

    RAISE NOTICE 'HJ-Palkkio: toimenpidekoodi %' , toimenpide_koodi;
    -- Hoidon johdon palkkiot koostuvat tehtäväryhmästä 'Hoidonjohtopalkkio (G)'
    -- sekä toimenpidekoodista 'Hoitourakan työnjohto'
    tehtavaryhma_id := (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoidonjohtopalkkio (G)');
    toimenpidekoodi_id := (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744');

    hj_palkkio_laskutettu := 0.0;
    hj_palkkio_laskutetaan := 0.0;

    IF (toimenpide_koodi = '23150') THEN

        RAISE NOTICE 'HJ-Palkkio lasketaan mukaan, koska toimenpideinstanssi on hoidon johto. %', t_instanssi;

        -- Ennen tarkasteltavaa aikaväliä laskutetut hoidonjohdon palkkiot - (päätellään tpi:stä ja toimenpidekoodista)
        -- Käydään läpi tiedot taulusta: kustannusarvioitu_tyo
        -- HJ-Palkkio - laskutettu
        FOR laskutettu_rivi IN SELECT (SELECT korotettuna
                                           FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi,
                                                                               indeksinimi, coalesce(kat.summa, 0),
                                                                               perusluku)) AS summa
                                   FROM kustannusarvioitu_tyo kat
                                   WHERE kat.toimenpideinstanssi = t_instanssi
                                     AND (kat.tehtavaryhma = tehtavaryhma_id OR kat.tehtava = toimenpidekoodi_id)
                                     AND kat.sopimus = sopimus_id
                                     AND (SELECT (date_trunc('MONTH',
                                                             format('%s-%s-%s', kat.vuosi, kat.kuukausi, 1)::DATE))) BETWEEN hk_alkupvm AND aikavali_loppupvm
                               UNION ALL
                               SELECT coalesce(lk.summa, 0) AS summa
                                   FROM lasku l
                                            JOIN lasku_kohdistus lk ON lk.lasku = l.id
                                   WHERE lk.toimenpideinstanssi = t_instanssi
                                     AND lk.poistettu IS NOT TRUE
                                     AND l.urakka = urakka_id
                                     AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
                                     AND lk.tehtavaryhma = tehtavaryhma_id

            LOOP
                RAISE NOTICE 'HJ-palkkio laskutettu :: summa: %', laskutettu_rivi.summa;
                hj_palkkio_laskutettu := hj_palkkio_laskutettu + COALESCE(laskutettu_rivi.summa, 0.0);
            END LOOP;

        -- Tarkasteltavalla aikavälillä laskutetut tai laskutettavat hoidonjohdon kustannukset
        -- Käydään läpi tiedot taulusta: kustannusarvioitu_tyo
        -- hj_palkkio - laskutetaan
        FOR hj_palkkio_laskutetaan_rivi IN SELECT (SELECT korotettuna
                                                       FROM laske_kuukauden_indeksikorotus(indeksi_vuosi,
                                                                                           indeksi_kuukausi,
                                                                                           indeksinimi,
                                                                                           coalesce(kat.summa, 0),
                                                                                           perusluku)) AS summa
                                               FROM kustannusarvioitu_tyo kat
                                               WHERE kat.toimenpideinstanssi = t_instanssi
                                                 AND (kat.tehtavaryhma = tehtavaryhma_id OR kat.tehtava = toimenpidekoodi_id)
                                                 AND kat.sopimus = sopimus_id
                                                 AND (SELECT (date_trunc('MONTH',
                                                                         format('%s-%s-%s', kat.vuosi, kat.kuukausi, 1)::DATE))) BETWEEN aikavali_alkupvm AND aikavali_loppupvm
                                           UNION ALL
                                           SELECT coalesce(lk.summa, 0) AS summa
                                               FROM lasku l
                                                        JOIN lasku_kohdistus lk ON lk.lasku = l.id
                                               WHERE lk.toimenpideinstanssi = t_instanssi
                                                 AND lk.poistettu IS NOT TRUE
                                                 AND l.urakka = urakka_id
                                                 AND l.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm
                                                 AND lk.tehtavaryhma = tehtavaryhma_id

            LOOP
                RAISE NOTICE 'HJ-palkkio laskutetaan :: summa: %', hj_palkkio_laskutetaan_rivi.summa;
                hj_palkkio_laskutetaan := hj_palkkio_laskutetaan + COALESCE(hj_palkkio_laskutetaan_rivi.summa, 0.0);
            END LOOP;
    END IF; -- tuotekoodi = 23150 (Hoidonjohto)

    rivi := (hj_palkkio_laskutettu, hj_palkkio_laskutetaan);
    RETURN NEXT rivi;
END;
$$ LANGUAGE plpgsql;

-- MHU hoidon johto on niin iso ja monimutkainen laskenta, että se on eriytetty tähän
DROP FUNCTION IF EXISTS hoidon_johto_yhteenveto(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                                   toimenpide_koodi TEXT, t_instanssi INTEGER, urakka_id INTEGER,
                                                   sopimus_id INTEGER, indeksi_vuosi INTEGER, indeksi_kuukausi INTEGER,
                                                   indeksinimi VARCHAR,
                                                   perusluku NUMERIC);
DROP TYPE IF EXISTS HOIDONJOHTO_RIVI;
CREATE TYPE HOIDONJOHTO_RIVI AS
(
    johto_ja_hallinto_laskutettu  NUMERIC,
    johto_ja_hallinto_laskutetaan NUMERIC
);

CREATE OR REPLACE FUNCTION hoidon_johto_yhteenveto(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                                   toimenpide_koodi TEXT, t_instanssi INTEGER, urakka_id INTEGER,
                                                   sopimus_id INTEGER, indeksi_vuosi INTEGER, indeksi_kuukausi INTEGER,
                                                   indeksinimi VARCHAR,
                                                   perusluku NUMERIC) RETURNS SETOF HOIDONJOHTO_RIVI AS
$$
DECLARE

    rivi                          HOIDONJOHTO_RIVI;
    johto_ja_hallinto_laskutettu  NUMERIC;
    johto_ja_hallinto_laskutetaan NUMERIC;
    laskutettu                    RECORD;
    laskutetaan                   RECORD;
    tehtavaryhma_id               INTEGER;
    toimistotarvike_koodi         INTEGER;


BEGIN
    -- Haetaan hoidon johdon yhteenvetoja tauluista: johto_ja_hallintokorvaus, lasku_kohdistus sekä kustannusarvioitu_tyo.
    -- lasku_kohdistustaulusta joudutaan hakemaan tarkkaan tehtäväryhmällä
    tehtavaryhma_id := (SELECT id FROM tehtavaryhma WHERE nimi = 'Johto- ja hallintokorvaus (J)');
    -- kustannusarvioitu_tyo taulusta haetaan toimenpidekoodin perusteella - Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.
    toimistotarvike_koodi := (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388');

    RAISE NOTICE 'hoidon_johto_yhteenveto: toimenpidekoodi %' , toimenpide_koodi;
    johto_ja_hallinto_laskutettu := 0.0;
    johto_ja_hallinto_laskutetaan := 0.0;

    IF (toimenpide_koodi = '23150') THEN

        RAISE NOTICE 'Johto- ja hallintakorvaukset lasketaan mukaan, koska toimenpideinstanssi on hoidon johto. %', t_instanssi;

        -- Ennen tarkasteltavaa aikaväliä ja aikavälillä laskutetut hoidonjohdon kustannukset
        -- Käytetään taulua: johto_ja_hallintokorvaus

        -- johto_ja_hallintokorvaus - laskutettu
        FOR laskutettu IN SELECT (SELECT korotettuna
                                      FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi, indeksinimi,
                                                                          (coalesce(jhk.tunnit, 0) * coalesce(jhk.tuntipalkka, 0)),
                                                                          perusluku)) AS summa
                              FROM johto_ja_hallintokorvaus jhk
                              WHERE "urakka-id" = urakka_id
                                AND (SELECT (date_trunc('MONTH',
                                                        format('%s-%s-%s', jhk.vuosi, jhk.kuukausi, 1)::DATE))) BETWEEN hk_alkupvm::DATE AND aikavali_loppupvm::DATE
                          UNION ALL
                          SELECT coalesce(lk.summa, 0) AS summa
                              FROM lasku l
                                       JOIN lasku_kohdistus lk ON lk.lasku = l.id
                              WHERE lk.toimenpideinstanssi = t_instanssi
                                AND lk.poistettu IS NOT TRUE
                                AND l.urakka = urakka_id
                                AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
                                AND lk.tehtavaryhma = tehtavaryhma_id
                          UNION ALL
                          SELECT (SELECT korotettuna
                                      FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi, indeksinimi,
                                                                          coalesce(kt.summa, 0), perusluku)) AS summa
                              FROM kustannusarvioitu_tyo kt
                              WHERE kt.toimenpideinstanssi = t_instanssi
                                AND kt.sopimus = sopimus_id
                                AND kt.tehtava = toimistotarvike_koodi -- Kustannussuunnitelmassa "Muut kulut" on toimistotarvikekuluja
                                AND (SELECT (date_trunc('MONTH',
                                                        format('%s-%s-%s', kt.vuosi, kt.kuukausi, 1)::DATE))) BETWEEN hk_alkupvm AND aikavali_loppupvm

            LOOP
                johto_ja_hallinto_laskutettu := johto_ja_hallinto_laskutettu + COALESCE(laskutettu.summa, 0.0);
            END LOOP;

        -- Tarkasteltavalla aikavälillä laskutetut tai laskutettavat hoidonjohdon kustannukset
        -- Käydään läpi tiedot taulusta johto_ja_hallintokorvaus
        -- Kuluvan kuukauden laskutettava summa nousee maksuerään vasta kuukauden viimeisenä päivänä.

        johto_ja_hallinto_laskutetaan := 0.0;

        -- johto_ja_hallintokorvaus - laskutetaan
        FOR laskutetaan IN SELECT (SELECT korotettuna
                                       FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi, indeksinimi,
                                                                           (coalesce(jhk.tunnit, 0) * coalesce(jhk.tuntipalkka, 0)),
                                                                           perusluku)) AS summa
                               FROM johto_ja_hallintokorvaus jhk
                               WHERE "urakka-id" = urakka_id
                                 AND (SELECT (date_trunc('MONTH',
                                                         format('%s-%s-%s', jhk.vuosi, jhk.kuukausi, 1)::DATE))) BETWEEN aikavali_alkupvm AND aikavali_loppupvm
                           UNION ALL
                           SELECT coalesce(lk.summa, 0) AS summa
                               FROM lasku l
                                        JOIN lasku_kohdistus lk ON lk.lasku = l.id
                               WHERE lk.toimenpideinstanssi = t_instanssi
                                 AND lk.poistettu IS NOT TRUE
                                 AND l.urakka = urakka_id
                                 AND l.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm
                                 AND lk.tehtavaryhma = tehtavaryhma_id
                           UNION ALL
                           SELECT (SELECT korotettuna
                                       FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi, indeksinimi,
                                                                           coalesce(kt.summa, 0), perusluku)) AS summa
                               FROM kustannusarvioitu_tyo kt
                               WHERE kt.toimenpideinstanssi = t_instanssi
                                 AND kt.sopimus = sopimus_id
                                 AND kt.tehtava = toimistotarvike_koodi -- Kustannussuunnitelmassa "Muut kulut" on toimistotarvikekuluja
                                 AND (SELECT (date_trunc('MONTH',
                                                         format('%s-%s-%s', kt.vuosi, kt.kuukausi, 1)::DATE))) BETWEEN aikavali_alkupvm AND aikavali_loppupvm

            LOOP
                -- Kuukauden laskutettava määrä päivittyy laskutettavaan summaan ja lähetettävään maksuerään vasta kuukauden viimeisenä päivänä.
                johto_ja_hallinto_laskutetaan := johto_ja_hallinto_laskutetaan + COALESCE(laskutetaan.summa, 0.0);
            END LOOP;
    END IF; -- tuotekoodi = 23150 (Hoidonjohto)

    rivi := (johto_ja_hallinto_laskutettu, johto_ja_hallinto_laskutetaan);
    RETURN NEXT rivi;
END;
$$ LANGUAGE plpgsql;

DROP FUNCTION IF EXISTS mhu_laskutusyhteenveto_teiden_hoito(hk_alkupvm DATE, hk_loppupvm DATE, aikavali_alkupvm DATE,
    aikavali_loppupvm DATE, ur INTEGER);

DROP TYPE IF EXISTS LASKUTUSYHTEENVETO_RAPORTTI_MHU_RIVI;

CREATE TYPE LASKUTUSYHTEENVETO_RAPORTTI_MHU_RIVI AS
(
    nimi                            VARCHAR,
    maksuera_numero                 NUMERIC,
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
    -- MHU ja HJU Hoidon johto
    johto_ja_hallinto_laskutettu    NUMERIC,
    johto_ja_hallinto_laskutetaan   NUMERIC,
    bonukset_laskutettu             NUMERIC,
    bonukset_laskutetaan            NUMERIC,
    hj_palkkio_laskutettu           NUMERIC,
    hj_palkkio_laskutetaan          NUMERIC,
    hj_erillishankinnat_laskutettu  NUMERIC,
    hj_erillishankinnat_laskutetaan NUMERIC,
    -- Asetukset
    suolasakko_kaytossa             BOOLEAN,
    lampotila_puuttuu               BOOLEAN,
    indeksi_puuttuu                 BOOLEAN
);

-- Palauttaa MHU laskutusyhteenvedossa tarvittavat summat
CREATE OR REPLACE FUNCTION mhu_laskutusyhteenveto_teiden_hoito(hk_alkupvm DATE, hk_loppupvm DATE,
                                                               aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                                               ur INTEGER) RETURNS SETOF LASKUTUSYHTEENVETO_RAPORTTI_MHU_RIVI
    LANGUAGE plpgsql AS
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

    -- MHU ja HJU Hoidon johto
    erilliskustannus_rivi                 RECORD;
    johto_ja_hallinto_laskutettu          NUMERIC;
    johto_ja_hallinto_laskutetaan         NUMERIC;
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
    rivi                                  LASKUTUSYHTEENVETO_RAPORTTI_MHU_RIVI;
    aikavali_kuukausi                     NUMERIC;
    aikavali_vuosi                        NUMERIC;
    hk_alkuvuosi                          NUMERIC;
    hk_alkukuukausi                       NUMERIC;
    indeksi_vuosi                         INTEGER;
    indeksi_kuukausi                      INTEGER;
    sopimus_id                            INTEGER;
    perusluku                             NUMERIC; -- urakan indeksilaskennan perusluku (urakkasopimusta edeltävän vuoden syys-,loka, marraskuun keskiarvo)
    indeksinimi                           VARCHAR; -- MAKU 2015
    indeksi_puuttuu                       BOOLEAN;
    indeksin_arvo                         NUMERIC;

BEGIN

    -- Hoitokauden alkukuukauteen perustuvaa indeksi käytetään kuluissa, joita urakoitsija ei itse ole syöttänyt, kuten bonuksissa, sanktioissa ja kustannusarvioiduissa_töissä.
    -- Muuten indeksiä ei käytetä
    perusluku := indeksilaskennan_perusluku(ur);
    RAISE NOTICE 'PERUSLUKU: %',perusluku;
    indeksinimi := (SELECT indeksi FROM urakka u WHERE u.id = ur);
    sopimus_id := (SELECT id FROM sopimus WHERE urakka = ur AND paasopimus IS NULL);

    aikavali_kuukausi := (SELECT EXTRACT(MONTH FROM aikavali_alkupvm) :: INTEGER);
    aikavali_vuosi := (SELECT EXTRACT(YEAR FROM aikavali_alkupvm) :: INTEGER);
    hk_alkuvuosi := (SELECT EXTRACT(YEAR FROM hk_alkupvm) :: INTEGER);
    hk_alkukuukausi := (SELECT EXTRACT(MONTH FROM hk_alkupvm) :: INTEGER);
    indeksi_vuosi := hk_alkuvuosi; -- Hoitokautta edeltävä syyskuu. Eli hoitokausi alkaa aina lokakuussa, niin se on se sama alkuvuosi. Esim. hoitourakka alkaa 2019 -> indeksi_vuosi on 2019
    indeksi_kuukausi := 9;
    indeksi_puuttuu := NULL;
    indeksin_arvo := (SELECT arvo
                          FROM indeksi
                          WHERE vuosi = indeksi_vuosi AND kuukausi = indeksi_kuukausi AND nimi = indeksinimi);

    IF indeksin_arvo > 0 THEN indeksi_puuttuu := FALSE; ELSE indeksi_puuttuu := TRUE; END IF;
    -- Aina syyskuu MHU urakoissa. Indeksi otetaan siis aina edellisen vuoden syyskuusta.

    -- Loopataan urakan toimenpideinstanssien läpi
    FOR t IN SELECT tpk2.nimi AS nimi, tpk2.koodi AS tuotekoodi, tpi.id AS tpi, tpk3.id AS tpk3_id, m.numero AS maksuera_numero
                 FROM toimenpideinstanssi tpi
                          JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                          JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
                      maksuera m
                 WHERE tpi.urakka = ur AND m.toimenpideinstanssi = tpi.id
        LOOP
            RAISE NOTICE '*************************************** Laskutusyhteenvedon laskenta alkaa toimenpiteelle: % , ID % *****************************************', t.nimi, t.tpi;

            -- Hoitokaudella ennen aikaväliä ja aikavälillä laskutetut lisätyöt
            lisatyot_laskutettu := 0.0;
            lisatyot_laskutetaan := 0.0;

            FOR lisatyot_rivi IN SELECT summa AS lisatyot_summa, l.erapaiva AS erapaiva
                                     FROM lasku l
                                              JOIN lasku_kohdistus lk ON lk.lasku = l.id
                                              JOIN toimenpideinstanssi tpi
                                                   ON lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
                                     WHERE lk.maksueratyyppi = 'lisatyo' -- TODO: Placeholder. Tällaista maksuerätyyppiä ei ole. Kiinteähintaiset lähetetään kokonaishintaisessa maksueraässä.
                                       AND lk.poistettu IS NOT TRUE
                                       AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
                LOOP
                    SELECT lisatyot_rivi.lisatyot_summa AS summa, 0::NUMERIC AS korotus INTO lisatyo;

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
            -- Paitsi hoidon johdon hankinnat, jotka on erillishankintoja ja ne on otettu huomioon eri kohdassa

            hankinnat_laskutettu := 0.0;
            hankinnat_laskutetaan := 0.0;

            IF (t.tuotekoodi != '23150') THEN
                FOR hankinnat_i IN SELECT summa AS kht_summa, l.erapaiva AS erapaiva
                                       FROM lasku l
                                                JOIN lasku_kohdistus lk ON lk.lasku = l.id
                                                JOIN toimenpideinstanssi tpi
                                                     ON lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
                                       WHERE lk.maksueratyyppi = 'kokonaishintainen' -- TODO: Sisältää kiinteähintaiset, kustannusarvioidut ja yksikkohintaiset työt
                                         AND lk.poistettu IS NOT TRUE
                                         AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
                    LOOP
                        SELECT hankinnat_i.kht_summa AS summa,
                               hankinnat_i.kht_summa AS korotettuna,
                               0::NUMERIC            AS korotus INTO hankinnat_rivi;

                        RAISE NOTICE 'hankinnat_rivi: % TPI %', hankinnat_rivi, t.tpi;
                        RAISE NOTICE 'hankinnat_rivi.summa: % TPI %', hankinnat_rivi.summa, t.tpi;

                        IF hankinnat_i.erapaiva <= aikavali_loppupvm THEN
                            -- Hoitokauden alusta
                            hankinnat_laskutettu := hankinnat_laskutettu + COALESCE(hankinnat_rivi.summa, 0.0);

                            IF hankinnat_i.erapaiva >= aikavali_alkupvm AND
                               hankinnat_i.erapaiva <= aikavali_loppupvm THEN
                                -- Laskutetaan nyt
                                hankinnat_laskutetaan := hankinnat_laskutetaan + COALESCE(hankinnat_rivi.summa, 0.0);
                            END IF;
                        END IF;

                        RAISE NOTICE 'hankinnat_laskutettu: %', hankinnat_laskutettu;
                        RAISE NOTICE 'hankinnat_laskutetaan: %', hankinnat_laskutetaan;
                    END LOOP;
            END IF;
            -- SANKTIOT
            -- Hoitokaudella ennen aikaväliä ja aikavaälillä laskutetut sanktiot
            -- Sanktioihin lasketaan indeksikorotukset matkaan hoitokautta edeltävän kuukauden indeksiarvolla - paitsi hoidonjohdon sanktioissa arvonvähennyksiin ei huomioida indeksiä
            sakot_laskutettu := 0.0;
            sakot_laskutetaan := 0.0;

            FOR sanktiorivi IN SELECT -maara                                                                    AS maara,
                                      perintapvm,
                                      indeksi,
                                      perintapvm,
                                      sakkoryhma,
                                      (SELECT korotettuna
                                           FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi,
                                                                               indeksinimi, -maara,
                                                                               perusluku))                      AS indeksikorotettuna
                                   FROM sanktio s
                                   WHERE s.toimenpideinstanssi = t.tpi
                                     AND s.maara IS NOT NULL
                                     AND s.perintapvm >= hk_alkupvm
                                     AND s.perintapvm <= aikavali_loppupvm
                                     AND s.poistettu IS NOT TRUE
                LOOP

                    -- TODO: Mikä tämä iffi on? Tuossa ylempänä jo rajataan hakua, tätä ei tartteta mihinkään
                    IF sanktiorivi.perintapvm <= aikavali_loppupvm THEN
                        -- Hoitokauden alusta
                        RAISE NOTICE 'sanktiorivi :: Määrä: %', sanktiorivi.maara;
                        RAISE NOTICE 'sanktiorivi :: indeksikorotettuna: % ', sanktiorivi.indeksikorotettuna;

                        IF sanktiorivi.sakkoryhma = 'arvonvahennyssanktio' THEN
                            sakot_laskutettu := sakot_laskutettu + COALESCE(sanktiorivi.maara, 0.0);
                        ELSE
                            sakot_laskutettu := sakot_laskutettu + COALESCE(sanktiorivi.indeksikorotettuna, 0.0);
                        END IF;


                        IF sanktiorivi.perintapvm >= aikavali_alkupvm AND
                           sanktiorivi.perintapvm <= aikavali_loppupvm THEN
                            -- Laskutetaan nyt
                            IF sanktiorivi.sakkoryhma = 'arvonvahennyssanktio' THEN
                                sakot_laskutetaan := sakot_laskutetaan + COALESCE(sanktiorivi.maara, 0.0);
                            ELSE
                                sakot_laskutetaan := sakot_laskutetaan + COALESCE(sanktiorivi.indeksikorotettuna, 0.0);
                            END IF;

                        END IF;
                    END IF;

                END LOOP;

            -- Onko suolasakko käytössä urakassa
            IF (SELECT count(*)
                    FROM suolasakko
                    WHERE urakka = ur
                      AND kaytossa
                      AND hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM hk_alkupvm) :: INTEGER)) > 0 THEN
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
                lampotilat_rivi.pitka_keskilampotila IS NULL) THEN
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
                FOR hoitokauden_suolasakko_rivi IN SELECT *
                                                       FROM suolasakko s
                                                       WHERE s.urakka = ur AND hk_alkuvuosi = s.hoitokauden_alkuvuosi
                    LOOP

                        RAISE NOTICE 'hoitokauden_suolasakko_rivi :: % ',hoitokauden_suolasakko_rivi;

                        hoitokauden_laskettu_suolasakon_maara :=
                                    (SELECT hoitokauden_suolasakko(ur, hk_alkupvm, hk_loppupvm));
                        RAISE NOTICE 'hoitokauden_laskettu_suolasakon_maara: %', hoitokauden_laskettu_suolasakon_maara;
                        -- Lasketaan suolasakolle indeksikorotus
                        hoitokauden_laskettu_suolasakon_maara := (SELECT korotettuna
                                                                      FROM laske_kuukauden_indeksikorotus(indeksi_vuosi,
                                                                                                          indeksi_kuukausi,
                                                                                                          indeksinimi,
                                                                                                          hoitokauden_laskettu_suolasakon_maara,
                                                                                                          perusluku));
                        RAISE NOTICE 'hoitokauden_laskettu_suolasakon_maara indeksikorotettuna: %', hoitokauden_laskettu_suolasakon_maara;

                        -- Jos suolasakko ei ole käytössä, ei edetä
                        IF (suolasakko_kaytossa = FALSE) THEN
                            RAISE NOTICE 'Suolasakko ei käytössä annetulla aikavälillä urakassa %, aikavali_alkupvm: %, hoitokauden_suolasakko_rivi: %', ur, aikavali_alkupvm, hoitokauden_suolasakko_rivi;
                            -- Suolasakko voi olla laskutettu jo hoitokaudella vain kk:ina 6-9 koska mahdolliset laskutus-kk:t ovat 5-9
                        ELSIF (hoitokauden_suolasakko_rivi.maksukuukausi < aikavali_kuukausi AND
                               aikavali_kuukausi < 10) OR
                              (hoitokauden_suolasakko_rivi.hoitokauden_alkuvuosi < aikavali_vuosi AND
                               hoitokauden_suolasakko_rivi.maksukuukausi < aikavali_kuukausi) THEN
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

            hj_palkkio_laskutettu := 0.0;
            hj_palkkio_laskutetaan := 0.0;
            johto_ja_hallinto_laskutettu := 0.0;
            johto_ja_hallinto_laskutetaan := 0.0;
            hj_erillishankinnat_laskutettu := 0.0;
            hj_erillishankinnat_laskutetaan := 0.0;

            -- Hoidonjohdolla (toimenpidekoodi 23150) omat erilliset mahdolliset kulunsa.
            IF (t.tuotekoodi = '23150') THEN
                FOR erilliskustannus_rivi IN SELECT ek.pvm, ek.rahasumma, ek.indeksin_nimi, ek.tyyppi
                                                 FROM erilliskustannus ek
                                                 WHERE ek.sopimus = sopimus_id
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
                                    alihank_bon_laskutetaan := alihank_bon_laskutetaan +
                                                               COALESCE(erilliskustannus_rivi.rahasumma, 0.0);
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
                                lupaus_bon_laskutettu :=
                                        lupaus_bon_laskutettu + COALESCE(lupaus_bon_rivi.korotettuna, 0.0);

                                IF erilliskustannus_rivi.pvm >= aikavali_alkupvm AND
                                   erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                                    -- Laskutetaan nyt
                                    lupaus_bon_laskutetaan :=
                                            lupaus_bon_laskutetaan + COALESCE(lupaus_bon_rivi.korotettuna, 0.0);

                                END IF;
                            END IF;
                            -- Asiakastyytyväisyysbonus
                        ELSEIF erilliskustannus_rivi.tyyppi = 'asiakastyytyvaisyysbonus' THEN
                            SELECT *
                                FROM laske_kuukauden_indeksikorotus(indeksi_vuosi, indeksi_kuukausi,
                                                                    erilliskustannus_rivi.indeksin_nimi,
                                                                    erilliskustannus_rivi.rahasumma, perusluku)
                                INTO asiakas_tyyt_bon_rivi;

                            IF erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                                -- Hoitokauden alusta
                                asiakas_tyyt_bon_laskutettu := asiakas_tyyt_bon_laskutettu +
                                                               COALESCE(asiakas_tyyt_bon_rivi.korotettuna, 0.0);

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
                                tavoitepalkk_bon_laskutettu := tavoitepalkk_bon_laskutettu +
                                                               COALESCE(erilliskustannus_rivi.rahasumma, 0.0);

                                IF erilliskustannus_rivi.pvm >= aikavali_alkupvm AND
                                   erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                                    -- Laskutetaan nyt
                                    tavoitepalkk_bon_laskutetaan := tavoitepalkk_bon_laskutetaan +
                                                                    COALESCE(erilliskustannus_rivi.rahasumma, 0.0);
                                END IF;
                            END IF;
                        END IF;
                    END LOOP;
                RAISE NOTICE 'Alihankintabonus laskutettu :: laskutetaan: % :: %', alihank_bon_laskutettu, alihank_bon_laskutetaan;
                RAISE NOTICE 'Lupausbonus laskutettu :: laskutetaan: % :: %', lupaus_bon_laskutettu, lupaus_bon_laskutetaan;
                RAISE NOTICE 'Asiakastyytyväisyysbonus laskutettu :: laskutetaan: % :: %', asiakas_tyyt_bon_laskutettu, asiakas_tyyt_bon_laskutetaan;
                RAISE NOTICE 'Tavoitepalkkio laskutettu :: laskutetaan: % :: %', tavoitepalkk_bon_laskutettu, tavoitepalkk_bon_laskutetaan;

                bonukset_laskutettu := bonukset_laskutettu + alihank_bon_laskutettu + lupaus_bon_laskutettu +
                                       asiakas_tyyt_bon_laskutettu + tavoitepalkk_bon_laskutettu;
                bonukset_laskutetaan := bonukset_laskutetaan + alihank_bon_laskutetaan + lupaus_bon_laskutetaan +
                                        asiakas_tyyt_bon_laskutetaan + tavoitepalkk_bon_laskutetaan;
                RAISE NOTICE 'Bonuksia laskutettu :: laskutetaan: % :: %', bonukset_laskutettu, bonukset_laskutetaan;

                -- HOIDON JOHTO, tpk 23150.
                -- Hoidon johdon kustannukset eli johto- ja hallintokorvays, erillishankinnat ja hoidonjohtopalkkio lasketaan maksuerään suoraan
                -- kustannussuunnitelmasta. Suunniteltu rahasumma siirtyy maksuerään kuukauden viimeisenä päivänä.
                -- Poikkeustapauksissa hoidon johdon kustannuksia kirjataan kulujen kohdistuksessa. Tällöin kustannukset lasketaan mukaan samaan tapaan kuin
                -- muutkin hankinnat (ks. kohdistetut_laskutetaan alla).
                h_rivi := (SELECT hoidon_johto_yhteenveto(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm, t.tuotekoodi,
                                                          t.tpi, ur, sopimus_id, indeksi_vuosi, indeksi_kuukausi,
                                                          indeksinimi, perusluku));

                johto_ja_hallinto_laskutettu := h_rivi.johto_ja_hallinto_laskutettu;
                johto_ja_hallinto_laskutetaan := h_rivi.johto_ja_hallinto_laskutetaan;

                -- HOIDONJOHTO --  HJ-Palkkio
                hj_palkkio_rivi :=
                        (SELECT hj_palkkio(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm, t.tuotekoodi, t.tpi, ur,
                                           sopimus_id, indeksi_vuosi, indeksi_kuukausi, indeksinimi, perusluku));
                hj_palkkio_laskutettu := hj_palkkio_rivi.hj_palkkio_laskutettu;
                hj_palkkio_laskutetaan := hj_palkkio_rivi.hj_palkkio_laskutetaan;

                -- HOIDONJOHTO --  erillishankinnat
                hj_erillishankinnat_rivi :=
                        (SELECT hj_erillishankinnat(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm, t.tuotekoodi,
                                                    t.tpi, ur, sopimus_id, indeksi_vuosi, indeksi_kuukausi, indeksinimi,
                                                    perusluku));
                hj_erillishankinnat_laskutettu := hj_erillishankinnat_rivi.hj_erillishankinnat_laskutettu;
                hj_erillishankinnat_laskutetaan := hj_erillishankinnat_rivi.hj_erillishankinnat_laskutetaan;

            END IF;
            -- Kustannusten kokonaissummat
            kaikki_laskutettu := 0.0;
            kaikki_laskutetaan := 0.0;
            kaikki_laskutettu := sakot_laskutettu + COALESCE(suolasakot_laskutettu, 0.0) + bonukset_laskutettu +
                                 hankinnat_laskutettu + lisatyot_laskutettu + johto_ja_hallinto_laskutettu +
                                 hj_palkkio_laskutettu + hj_erillishankinnat_laskutettu;

            kaikki_laskutetaan := sakot_laskutetaan + COALESCE(suolasakot_laskutetaan, 0.0) + bonukset_laskutetaan +
                                  hankinnat_laskutetaan + lisatyot_laskutetaan + johto_ja_hallinto_laskutetaan +
                                  hj_palkkio_laskutetaan + hj_erillishankinnat_laskutetaan;

            -- Tavoitehintaan sisältyy: Hankinnat, Johto- ja Hallintokorvaukset, (hoidonjohto tässä), Erillishankinnat, HJ-Palkkio.
            -- Tavoitehintaan ei sisälly: Lisätyöt, Sanktiot, Suolasanktiot, Bonukset
            tavoitehintaiset_laskutettu :=
                        hankinnat_laskutettu + johto_ja_hallinto_laskutettu + hj_erillishankinnat_laskutettu +
                        hj_palkkio_laskutettu;

            tavoitehintaiset_laskutetaan :=
                        hankinnat_laskutetaan + johto_ja_hallinto_laskutetaan + hj_erillishankinnat_laskutetaan +
                        hj_palkkio_laskutetaan;

            RAISE NOTICE '
    Yhteenveto:';
            RAISE NOTICE 'LASKUTETTU ENNEN AIKAVÄLIÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
            RAISE NOTICE 'Lisatyot laskutettu: %', lisatyot_laskutettu;
            RAISE NOTICE 'Hankinnat laskutettu: %', hankinnat_laskutettu;
            RAISE NOTICE 'Sakot laskutettu: %', sakot_laskutettu;
            RAISE NOTICE 'Suolasakot laskutettu: %', suolasakot_laskutettu;
            RAISE NOTICE 'Johto- ja Hallintokorvaus laskutettu: %', johto_ja_hallinto_laskutettu;
            RAISE NOTICE 'Erillishankinnat laskutettu: %', hj_erillishankinnat_laskutettu;
            RAISE NOTICE 'HJ-Palkkio laskutettu: %', hj_palkkio_laskutettu;
            RAISE NOTICE 'Bonukset laskutettu: %', bonukset_laskutettu;


            RAISE NOTICE '
LASKUTETAAN AIKAVÄLILLÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
            RAISE NOTICE 'Lisatyot laskutetaan: %', lisatyot_laskutetaan;
            RAISE NOTICE 'Hankinnat laskutetaan: %', hankinnat_laskutetaan;
            RAISE NOTICE 'Sakot laskutetaan: %', sakot_laskutetaan;
            RAISE NOTICE 'Suolasakot laskutetaan: %', suolasakot_laskutetaan;
            RAISE NOTICE 'Johto- ja hallintokorvaus laskutetaan: %', johto_ja_hallinto_laskutetaan;
            RAISE NOTICE 'Erillishankinnat laskutetaan: %', hj_erillishankinnat_laskutetaan;
            RAISE NOTICE 'HJ-Palkkio laskutetaan: %', hj_palkkio_laskutetaan;
            RAISE NOTICE 'Bonukset laskutetaan: %', bonukset_laskutetaan;

            RAISE NOTICE 'Kaikki laskutettu: %', kaikki_laskutettu;
            RAISE NOTICE 'Kaikki laskutetaan: %', kaikki_laskutetaan;

            RAISE NOTICE 'Tavoitehintaiset laskutettu: %', tavoitehintaiset_laskutettu;
            RAISE NOTICE 'Tavoitehintaiset laskutetaan: %', tavoitehintaiset_laskutetaan;

            RAISE NOTICE 'Suolasakko käytössä: %', suolasakko_kaytossa;
            RAISE NOTICE 'Läpmötila puuttuu: %', lampotila_puuttuu;

            RAISE NOTICE '********************************** Käsitelly loppui toimenpiteelle: %  *************************************
    ', t.nimi;

            rivi := (t.nimi, t.maksuera_numero, t.tuotekoodi, t.tpi, perusluku,
                     kaikki_laskutettu, kaikki_laskutetaan,
                     tavoitehintaiset_laskutettu, tavoitehintaiset_laskutetaan,
                     lisatyot_laskutettu, lisatyot_laskutetaan,
                     hankinnat_laskutettu, hankinnat_laskutetaan,
                     sakot_laskutettu, sakot_laskutetaan,
                     suolasakot_laskutettu, suolasakot_laskutetaan,
                     johto_ja_hallinto_laskutettu, johto_ja_hallinto_laskutetaan,
                     bonukset_laskutettu, bonukset_laskutetaan,
                     hj_palkkio_laskutettu, hj_palkkio_laskutetaan,
                     hj_erillishankinnat_laskutettu, hj_erillishankinnat_laskutetaan,
                     suolasakko_kaytossa, lampotila_puuttuu, indeksi_puuttuu
                );

            RETURN NEXT rivi;
        END LOOP;
END;
$$;
