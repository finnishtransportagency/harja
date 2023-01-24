-- ;; TODO: Lisää kustannusarvioitu_tyo ja johto_ja_hallintokorvaus tauluista tuleviin datoihin indeksikorjaukset
-- MHU-urakoiden laskutusyhteeneveto


-- MHU hoidonjohdon hoitokauden päättämiseen liittyvät kulut
DROP FUNCTION IF EXISTS hj_hoitovuoden_paattaminen_tavoitepalkkio (hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
    toimenpide_koodi TEXT, t_instanssi INTEGER, urakka_id INTEGER, sopimus_id INTEGER, indeksi_vuosi INTEGER,
    indeksi_kuukausi INTEGER, indeksinimi VARCHAR, perusluku NUMERIC, pyorista_kerroin BOOLEAN);

DROP TYPE IF EXISTS HJHOITOKAUDENPAATTAMINEN_RIVI CASCADE;
CREATE TYPE HJHOITOKAUDENPAATTAMINEN_RIVI AS
(
    hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu  NUMERIC,
    hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan NUMERIC,
    hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu  NUMERIC,
    hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan NUMERIC,
    hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu  NUMERIC,
    hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan NUMERIC
);

CREATE OR REPLACE FUNCTION hj_hoitovuoden_paattaminen_kulut(hk_alkupvm DATE, aikavali_alkupvm DATE,
                                                            aikavali_loppupvm DATE, t_instanssi INTEGER,
                                                            tehtavaryhma_id INTEGER, urakka_id INTEGER)
    RETURNS SETOF NUMERIC[] AS
$$
DECLARE

    hj_laskutettu_rivi                 RECORD;
    hj_laskutetaan_rivi                RECORD;
    hj_laskutettu                      NUMERIC;
    hj_laskutetaan                     NUMERIC;

    BEGIN

    hj_laskutettu = 0.0;
    hj_laskutetaan = 0.0;

    -- Laskutettu. Ennen tarkasteltavaa aikaväliä laskutetut, tehtäväryhmän mukaiset, hoitovuoden päättämiseen liittyvät kulut
    FOR hj_laskutettu_rivi IN SELECT coalesce(lk.summa, 0) AS summa
                              FROM kulu l
                                       JOIN kulu_kohdistus lk ON lk.kulu = l.id
                              WHERE lk.toimenpideinstanssi = t_instanssi
                                AND lk.poistettu IS NOT TRUE
                                AND l.urakka = urakka_id
                                AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
                                AND lk.tehtavaryhma = tehtavaryhma_id
        LOOP
            RAISE NOTICE 'Hoitovuoden päättäminen (tehtäväryhmä %), laskutettu :: summa: %', tehtavaryhma_id, hj_laskutettu_rivi.summa;
            hj_laskutettu := hj_laskutettu + COALESCE(hj_laskutettu_rivi.summa, 0.0);
        END LOOP;

    -- Laskutetaan. Tarkasteltavalla aikavälillä laskutettavat, tehtäväryhmän mukaiset, hoitovuoden päättämiseen liittyvät kulut
    FOR hj_laskutetaan_rivi IN SELECT coalesce(lk.summa, 0) AS summa
                               FROM kulu l
                                        JOIN kulu_kohdistus lk ON lk.kulu = l.id
                               WHERE lk.toimenpideinstanssi = t_instanssi
                                 AND lk.poistettu IS NOT TRUE
                                 AND l.urakka = urakka_id
                                 AND l.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm
                                 AND lk.tehtavaryhma = tehtavaryhma_id
        LOOP
            RAISE NOTICE 'Hoitovuoden päättäminen (tehtäväryhmä %) laskutetaan :: summa: %', tehtavaryhma_id, hj_laskutetaan_rivi.summa;
            hj_laskutetaan := hj_laskutetaan + COALESCE(hj_laskutetaan_rivi.summa, 0.0);
        END LOOP;
    RETURN NEXT ARRAY [hj_laskutettu, hj_laskutetaan];
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION hj_hoitovuoden_paattaminen(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                               toimenpide_koodi TEXT, t_instanssi INTEGER, urakka_id INTEGER,
                                               sopimus_id INTEGER) RETURNS SETOF HJHOITOKAUDENPAATTAMINEN_RIVI AS
$$
DECLARE

    rivi                                                        HJHOITOKAUDENPAATTAMINEN_RIVI;
    hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu        NUMERIC;
    hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan       NUMERIC;
    hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu  NUMERIC;
    hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan NUMERIC;
    hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu    NUMERIC;
    hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan   NUMERIC;
    tehtavaryhma_id                                             INTEGER;
    kulut                                                       NUMERIC[];

BEGIN
    IF (toimenpide_koodi = '23150') THEN

        -- Haetaan hoitokauden päättämiseen liittyvät kulut
        RAISE NOTICE '** HOITOVUODEN PÄÄTTÄMINEN **';
        RAISE NOTICE 'Hoitovuoden päättämiseen liittyvät kulut otetaan mukaan, koska toimenpideinstanssi on hoidon johto: %. Toimenpidekoodi on %.', t_instanssi, toimenpide_koodi;

        -- HOITOVUODEN PÄÄTTÄMINEN, TAVOITEPALKKIO
        tehtavaryhma_id := (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoitovuoden päättäminen / Tavoitepalkkio');
        RAISE NOTICE '    Tavoitepalkkio. Tehtavaryhma_id: % ' , tehtavaryhma_id;

        kulut = hj_hoitovuoden_paattaminen_kulut (hk_alkupvm, aikavali_alkupvm,
                                                  aikavali_loppupvm, t_instanssi,
                                                  tehtavaryhma_id, urakka_id);
        hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu := kulut[1];
        hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan := kulut[2];

        -- HOITOVUODEN PÄÄTTÄMINEN, TAVOITEHINNAN YLITYS
        tehtavaryhma_id := (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä');
        RAISE NOTICE '    Tavoitehinnan ylitys. Tehtavaryhma_id: % ' , tehtavaryhma_id;

        kulut = hj_hoitovuoden_paattaminen_kulut (hk_alkupvm, aikavali_alkupvm,
                                                  aikavali_loppupvm, t_instanssi,
                                                  tehtavaryhma_id, urakka_id);
        hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu := kulut[1];
        hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan := kulut[2];

        -- HOITOVUODEN PÄÄTTÄMINEN, KATTOHINNAN YLITYS
        tehtavaryhma_id := (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä');
        RAISE NOTICE '   Kattohinnan ylitys : Tehtavaryhma_id: % ' , tehtavaryhma_id;

        kulut = hj_hoitovuoden_paattaminen_kulut (hk_alkupvm, aikavali_alkupvm,
                                                  aikavali_loppupvm, t_instanssi,
                                                  tehtavaryhma_id, urakka_id);
        hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu := kulut[1];
        hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan := kulut[2];

    END IF; -- tuotekoodi = 23150 (Hoidonjohto)

    rivi := (hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu, hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan,
             hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu, hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan,
             hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu, hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan);
    RETURN NEXT rivi;
END;
$$ LANGUAGE plpgsql;

-- MHU hoidonjohdon erillishankinnat
-- Dropataan funktiot.
-- Näitä tarvitaan useampi, koska tuotannossa on aivan erilainen versio menossa tästä funktiosta verrattuna ci putkeen, joka tehdään tyhjästä
DROP FUNCTION IF EXISTS hj_erillishankinnat (hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
    toimenpide_koodi TEXT, t_instanssi INTEGER, urakka_id INTEGER, sopimus_id INTEGER, indeksi_vuosi INTEGER,
    indeksi_kuukausi INTEGER, indeksinimi VARCHAR, perusluku NUMERIC, pyorista_kerroin BOOLEAN);
DROP FUNCTION IF EXISTS hj_erillishankinnat (hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                             toimenpide_koodi TEXT, t_instanssi INTEGER, urakka_id INTEGER, sopimus_id INTEGER, indeksi_vuosi INTEGER,
                                             indeksi_kuukausi INTEGER, indeksinimi VARCHAR, perusluku NUMERIC);
DROP TYPE IF EXISTS HJERILLISHANKINNAT_RIVI CASCADE;
CREATE TYPE HJERILLISHANKINNAT_RIVI AS
(
    hj_erillishankinnat_laskutettu  NUMERIC,
    hj_erillishankinnat_laskutetaan NUMERIC
);
CREATE OR REPLACE FUNCTION hj_erillishankinnat(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                               toimenpide_koodi TEXT, t_instanssi INTEGER, urakka_id INTEGER,
                                               sopimus_id INTEGER) RETURNS SETOF HJERILLISHANKINNAT_RIVI AS
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
    tehtavaryhma_id := (SELECT id FROM tehtavaryhma WHERE nimi = 'Erillishankinnat (W)');
    RAISE NOTICE 'hj_erillishankinnat: toimenpidekoodi % -- tehtavaryhma_i: % ' , toimenpide_koodi, tehtavaryhma_id;

    hj_erillishankinnat_laskutettu := 0.0;
    hj_erillishankinnat_laskutetaan := 0.0;

    IF (toimenpide_koodi = '23150') THEN

        RAISE NOTICE 'hj_erillishankinnat lasketaan mukaan, koska toimenpideinstanssi on hoidon johto. %', t_instanssi;

        -- Ennen tarkasteltavaa aikaväliä laskutetut hoidonjohdon erillishankinnat - (päätellään tpi:stä ja toimenpidekoodista )
        -- Käydään läpi tiedot tauluista: kustannusarvioitu_tyo ja kulu_kohdistus
        -- Laskutettu
        FOR laskutettu_rivi IN SELECT coalesce(kat.summa_indeksikorjattu, kat.summa, 0) AS summa -- Ota indeksikorjattu summa, jos se on
                                   FROM kustannusarvioitu_tyo kat
                                   WHERE kat.toimenpideinstanssi = t_instanssi
                                     AND kat.tehtavaryhma = tehtavaryhma_id
                                     AND kat.sopimus = sopimus_id
                                     AND (SELECT (date_trunc('MONTH',
                                                             format('%s-%s-%s', kat.vuosi, kat.kuukausi, 1)::DATE))) BETWEEN hk_alkupvm AND aikavali_loppupvm
                               UNION ALL
                               SELECT coalesce(lk.summa, 0) AS summa
                                   FROM kulu l
                                            JOIN kulu_kohdistus lk ON lk.kulu = l.id
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
        -- Käydään läpi tiedot tauluista: kustannusarvioitu_tyo ja kulu_kohdistus
        -- Laskutetaan
        FOR laskutetaan_rivi IN SELECT coalesce(kat.summa_indeksikorjattu, kat.summa, 0) AS summa
                                    FROM kustannusarvioitu_tyo kat
                                    WHERE kat.toimenpideinstanssi = t_instanssi
                                      AND kat.sopimus = sopimus_id
                                      AND kat.tehtavaryhma = tehtavaryhma_id
                                      AND (SELECT (date_trunc('MONTH',
                                                              format('%s-%s-%s', kat.vuosi, kat.kuukausi, 1)::DATE))) BETWEEN aikavali_alkupvm AND aikavali_loppupvm
                                UNION ALL
                                SELECT coalesce(lk.summa, 0) AS summa
                                    FROM kulu l
                                             JOIN kulu_kohdistus lk ON lk.kulu = l.id
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

-- Dropataan eri ympäristöjen takia useampi versio
DROP FUNCTION IF EXISTS hj_palkkio(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE, toimenpide_koodi TEXT,
                                      t_instanssi INTEGER, urakka_id INTEGER,
                                      sopimus_id INTEGER, indeksi_vuosi INTEGER, indeksi_kuukausi INTEGER,
                                      indeksinimi VARCHAR, perusluku NUMERIC, pyorista_kerroin BOOLEAN);
DROP FUNCTION IF EXISTS hj_palkkio(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE, toimenpide_koodi TEXT,
                                   t_instanssi INTEGER, urakka_id INTEGER,
                                   sopimus_id INTEGER, indeksi_vuosi INTEGER, indeksi_kuukausi INTEGER,
                                   indeksinimi VARCHAR, perusluku NUMERIC);

-- MHU hoidonjohdon palkkio pilkotaan tähän
DROP TYPE IF EXISTS HJPALKKIO_RIVI CASCADE;
CREATE TYPE HJPALKKIO_RIVI AS
(
    hj_palkkio_laskutettu  NUMERIC,
    hj_palkkio_laskutetaan NUMERIC
);

CREATE OR REPLACE FUNCTION hj_palkkio(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                      toimenpide_koodi TEXT, t_instanssi INTEGER, urakka_id INTEGER, sopimus_id INTEGER)
                                      RETURNS SETOF HJPALKKIO_RIVI AS
$$
DECLARE

    rivi                            HJPALKKIO_RIVI;
    hj_palkkio_laskutettu           NUMERIC;
    hj_palkkio_laskutetaan          NUMERIC;
    laskutettu_rivi                 RECORD;
    hj_palkkio_laskutetaan_rivi     RECORD;
    tehtavaryhma_id                 INTEGER;
    toimenpidekoodi_id_hu_tyonjohto INTEGER;
    toimenpidekoodi_id_hj_palkkio   INTEGER;

BEGIN
    -- Haetaan hoidon johdon palkkiot

    RAISE NOTICE 'HJ-Palkkio: toimenpidekoodi %' , toimenpide_koodi;
    -- Hoidon johdon palkkiot koostuvat tehtäväryhmästä 'Hoidonjohtopalkkio (G)'
    -- sekä toimenpidekoodista 'Hoitourakan työnjohto' JA 'Hoidonjohtopalkkio'
    tehtavaryhma_id := (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoidonjohtopalkkio (G)');
    toimenpidekoodi_id_hu_tyonjohto := (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744');
    toimenpidekoodi_id_hj_palkkio := (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8');

    hj_palkkio_laskutettu := 0.0;
    hj_palkkio_laskutetaan := 0.0;

    IF (toimenpide_koodi = '23150') THEN

        RAISE NOTICE 'HJ-Palkkio lasketaan mukaan, koska toimenpideinstanssi on hoidon johto. %', t_instanssi;

        -- Ennen tarkasteltavaa aikaväliä laskutetut hoidonjohdon palkkiot - (päätellään tpi:stä ja toimenpidekoodista)
        -- Käydään läpi tiedot taulusta: kustannusarvioitu_tyo
        -- HJ-Palkkio - laskutettu
        FOR laskutettu_rivi IN SELECT coalesce(kat.summa_indeksikorjattu, kat.summa, 0) AS summa
                                   FROM kustannusarvioitu_tyo kat
                                   WHERE kat.toimenpideinstanssi = t_instanssi
                                     AND (kat.tehtavaryhma = tehtavaryhma_id OR kat.tehtava IN (toimenpidekoodi_id_hu_tyonjohto, toimenpidekoodi_id_hj_palkkio))
                                     AND kat.sopimus = sopimus_id
                                     AND (SELECT (date_trunc('MONTH',
                                                             format('%s-%s-%s', kat.vuosi, kat.kuukausi, 1)::DATE))) BETWEEN hk_alkupvm AND aikavali_loppupvm
                               UNION ALL
                               SELECT coalesce(lk.summa, 0) AS summa
                                   FROM kulu l
                                            JOIN kulu_kohdistus lk ON lk.kulu = l.id
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
        FOR hj_palkkio_laskutetaan_rivi IN SELECT coalesce(kat.summa_indeksikorjattu, kat.summa, 0) AS summa
                                               FROM kustannusarvioitu_tyo kat
                                               WHERE kat.toimenpideinstanssi = t_instanssi
                                                 AND (kat.tehtavaryhma = tehtavaryhma_id OR kat.tehtava IN (toimenpidekoodi_id_hu_tyonjohto, toimenpidekoodi_id_hj_palkkio))
                                                 AND kat.sopimus = sopimus_id
                                                 AND (SELECT (date_trunc('MONTH',
                                                                         format('%s-%s-%s', kat.vuosi, kat.kuukausi, 1)::DATE))) BETWEEN aikavali_alkupvm AND aikavali_loppupvm
                                           UNION ALL
                                           SELECT coalesce(lk.summa, 0) AS summa
                                               FROM kulu l
                                                        JOIN kulu_kohdistus lk ON lk.kulu = l.id
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
-- Dropataan useampi funktio, koska on eri versioita eri ympäristöissä
DROP FUNCTION IF EXISTS hoidon_johto_yhteenveto(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                                   toimenpide_koodi TEXT, t_instanssi INTEGER, urakka_id INTEGER,
                                                   sopimus_id INTEGER, indeksi_vuosi INTEGER, indeksi_kuukausi INTEGER,
                                                   indeksinimi VARCHAR,
                                                   perusluku NUMERIC, pyorista_kerroin BOOLEAN);
DROP FUNCTION IF EXISTS hoidon_johto_yhteenveto(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                                toimenpide_koodi TEXT, t_instanssi INTEGER, urakka_id INTEGER,
                                                sopimus_id INTEGER, indeksi_vuosi INTEGER, indeksi_kuukausi INTEGER,
                                                indeksinimi VARCHAR, perusluku NUMERIC);
DROP TYPE IF EXISTS HOIDONJOHTO_RIVI CASCADE;
CREATE TYPE HOIDONJOHTO_RIVI AS
(
    johto_ja_hallinto_laskutettu  NUMERIC,
    johto_ja_hallinto_laskutetaan NUMERIC
);

CREATE OR REPLACE FUNCTION hoidon_johto_yhteenveto(hk_alkupvm DATE, aikavali_alkupvm DATE, aikavali_loppupvm DATE,
                                                   toimenpide_koodi TEXT, t_instanssi INTEGER, urakka_id INTEGER,
                                                   sopimus_id INTEGER) RETURNS SETOF HOIDONJOHTO_RIVI AS
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
    -- Haetaan hoidon johdon yhteenvetoja tauluista: johto_ja_hallintokorvaus, kulu_kohdistus sekä kustannusarvioitu_tyo.
    -- kustannusarvioitu_tyo haetaan pelkästään tehtävällä, koska tehtäväryhmät viittaavat aina Tavoitehinnan ulkopuolisiin rahavarauksiin
    tehtavaryhma_id := (SELECT id FROM tehtavaryhma WHERE nimi = 'Johto- ja hallintokorvaus (J)');
    -- kustannusarvioitu_tyo taulusta haetaan toimenpidekoodin perusteella - Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.
    toimistotarvike_koodi :=
            (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388');

    RAISE NOTICE 'hoidon_johto_yhteenveto: toimenpidekoodi %' , toimenpide_koodi;
    johto_ja_hallinto_laskutettu := 0.0;
    johto_ja_hallinto_laskutetaan := 0.0;

    IF (toimenpide_koodi = '23150') THEN

        RAISE NOTICE 'Johto- ja hallintakorvaukset lasketaan mukaan, koska toimenpideinstanssi on hoidon johto. %', t_instanssi;

        -- Ennen tarkasteltavaa aikaväliä ja aikavälillä laskutetut hoidonjohdon kustannukset
        -- Käytetään taulua: johto_ja_hallintokorvaus

        -- johto_ja_hallintokorvaus - laskutettu
        FOR laskutettu IN
            SELECT CASE
                       WHEN jhk.tuntipalkka_indeksikorjattu IS NOT NULL
                           THEN coalesce((jhk.tunnit * jhk.tuntipalkka_indeksikorjattu * jhk."osa-kuukaudesta"), 0)
                       ELSE coalesce((jhk.tunnit * jhk.tuntipalkka * jhk."osa-kuukaudesta"), 0)
                       END
                       AS summa
            FROM johto_ja_hallintokorvaus jhk
            WHERE "urakka-id" = urakka_id
              AND (SELECT (date_trunc('MONTH',
                                      format('%s-%s-%s', jhk.vuosi, jhk.kuukausi, 1)::DATE))) BETWEEN hk_alkupvm::DATE AND aikavali_loppupvm::DATE

            UNION ALL

            SELECT coalesce(lk.summa, 0) AS summa
            FROM kulu l
                 JOIN kulu_kohdistus lk ON lk.kulu = l.id
            WHERE lk.toimenpideinstanssi = t_instanssi
              AND lk.poistettu IS NOT TRUE
              AND l.urakka = urakka_id
              AND l.erapaiva BETWEEN hk_alkupvm AND aikavali_loppupvm
              AND lk.tehtavaryhma = tehtavaryhma_id

            UNION ALL

            SELECT coalesce(kt.summa_indeksikorjattu, kt.summa, 0) AS summa
            FROM kustannusarvioitu_tyo kt
            WHERE kt.toimenpideinstanssi = t_instanssi
              AND kt.sopimus = sopimus_id
              AND kt.tehtava = toimistotarvike_koodi -- Kustannussuunnitelmassa "Muut kulut" on toimistotarvikekuluja
              AND (SELECT (date_trunc('MONTH', format('%s-%s-%s', kt.vuosi, kt.kuukausi, 1)::DATE)))
                  BETWEEN hk_alkupvm AND aikavali_loppupvm

            LOOP
                johto_ja_hallinto_laskutettu := johto_ja_hallinto_laskutettu + COALESCE(laskutettu.summa, 0.0);
            END LOOP;

        -- Tarkasteltavalla aikavälillä laskutetut tai laskutettavat hoidonjohdon kustannukset
        -- Käydään läpi tiedot taulusta johto_ja_hallintokorvaus
        -- Kuluvan kuukauden laskutettava summa nousee maksuerään vasta kuukauden viimeisenä päivänä.

        johto_ja_hallinto_laskutetaan := 0.0;

        -- johto_ja_hallintokorvaus - laskutetaan
        FOR laskutetaan IN
            SELECT CASE
                       WHEN jhk.tuntipalkka_indeksikorjattu IS NOT NULL
                           THEN coalesce((jhk.tunnit * jhk.tuntipalkka_indeksikorjattu * jhk."osa-kuukaudesta"), 0)
                       ELSE coalesce((jhk.tunnit * jhk.tuntipalkka * jhk."osa-kuukaudesta"), 0)
                       END
                       AS summa
            FROM johto_ja_hallintokorvaus jhk
            WHERE "urakka-id" = urakka_id
              AND (SELECT (date_trunc('MONTH', format('%s-%s-%s', jhk.vuosi, jhk.kuukausi, 1)::DATE)))
                  BETWEEN aikavali_alkupvm AND aikavali_loppupvm

            UNION ALL

            SELECT coalesce(lk.summa, 0) AS summa
            FROM kulu l
                     JOIN kulu_kohdistus lk ON lk.kulu = l.id
            WHERE lk.toimenpideinstanssi = t_instanssi
              AND lk.poistettu = FALSE
              AND l.urakka = urakka_id
              AND l.erapaiva BETWEEN aikavali_alkupvm AND aikavali_loppupvm
              AND lk.tehtavaryhma = tehtavaryhma_id

            UNION ALL

            SELECT coalesce(kt.summa_indeksikorjattu, kt.summa, 0) AS summa
            FROM kustannusarvioitu_tyo kt
            WHERE kt.toimenpideinstanssi = t_instanssi
              AND kt.sopimus = sopimus_id
              AND kt.tehtava = toimistotarvike_koodi
              AND (SELECT (date_trunc('MONTH',
                                      format('%s-%s-%s', kt.vuosi, kt.kuukausi, 1)::DATE)))
                  BETWEEN aikavali_alkupvm AND aikavali_loppupvm

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

DROP TYPE IF EXISTS LASKUTUSYHTEENVETO_RAPORTTI_MHU_RIVI CASCADE;

CREATE TYPE LASKUTUSYHTEENVETO_RAPORTTI_MHU_RIVI AS
(
    nimi                                                        VARCHAR,
    maksuera_numero                                             NUMERIC,
    tuotekoodi                                                  VARCHAR,
    tpi                                                         INTEGER,
    perusluku                                                   NUMERIC,
    kaikki_laskutettu                                           NUMERIC,
    kaikki_laskutetaan                                          NUMERIC,
    tavoitehintaiset_laskutettu                                 NUMERIC,
    tavoitehintaiset_laskutetaan                                NUMERIC,
    lisatyot_laskutettu                                         NUMERIC,
    lisatyot_laskutetaan                                        NUMERIC,
    hankinnat_laskutettu                                        NUMERIC,
    hankinnat_laskutetaan                                       NUMERIC,
    sakot_laskutettu                                            NUMERIC,
    sakot_laskutetaan                                           NUMERIC,
    -- MHU ja HJU Hoidon johto
    johto_ja_hallinto_laskutettu                                NUMERIC,
    johto_ja_hallinto_laskutetaan                               NUMERIC,
    bonukset_laskutettu                                         NUMERIC,
    bonukset_laskutetaan                                        NUMERIC,
    hj_palkkio_laskutettu                                       NUMERIC,
    hj_palkkio_laskutetaan                                      NUMERIC,
    hj_erillishankinnat_laskutettu                              NUMERIC,
    hj_erillishankinnat_laskutetaan                             NUMERIC,
    hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu        NUMERIC,
    hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan       NUMERIC,
    hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu  NUMERIC,
    hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan NUMERIC,
    hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu    NUMERIC,
    hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan   NUMERIC,
    -- Asetukset
    indeksi_puuttuu                                             BOOLEAN
);

-- Palauttaa MHU laskutusyhteenvedossa tarvittavat summat
CREATE OR REPLACE FUNCTION mhu_laskutusyhteenveto_teiden_hoito(hk_alkupvm DATE, hk_loppupvm DATE, aikavali_alkupvm DATE,
                                                               aikavali_loppupvm DATE, ur INTEGER)
                                                               RETURNS SETOF LASKUTUSYHTEENVETO_RAPORTTI_MHU_RIVI
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
    muu_bonus_laskutettu                  NUMERIC;
    muu_bonus_laskutetaan                 NUMERIC;
    muu_bonus_rivi                        RECORD;
    tavoitehinnan_ulk_rahav_laskutettu    NUMERIC;
    tavoitehinnan_ulk_rahav_laskutetaan   NUMERIC;
    tavoitehinnan_ulk_rahav_rivi          RECORD;
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
    -- Hoitokauden päättämiseen liittyvät kulut
    hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu        NUMERIC;
    hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan       NUMERIC;
    hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu  NUMERIC;
    hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan NUMERIC;
    hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu    NUMERIC;
    hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan   NUMERIC;
    hj_hoitovuoden_paattaminen_rivi                             HJHOITOKAUDENPAATTAMINEN_RIVI;

    -- Asetuksia
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
    pyorista_kerroin                      BOOLEAN;
    johto_ja_hallintakorvaus_toimenpideinstanssi_id NUMERIC;

BEGIN

    -- Hoitokauden alkukuukauteen perustuvaa indeksi käytetään kuluissa, joita urakoitsija ei itse ole syöttänyt, kuten bonuksissa, sanktioissa ja kustannusarvioiduissa_töissä.
    -- Muuten indeksiä ei käytetä
    perusluku := indeksilaskennan_perusluku(ur);
    pyorista_kerroin := TRUE; -- MH-urakoissa pyöristestään indeksikerroin kolmeen desimaaliin (eli prosentin kymmenykseen).
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

    johto_ja_hallintakorvaus_toimenpideinstanssi_id := (SELECT tpi.id AS id
                                                     FROM toimenpideinstanssi tpi
                                                              JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                                                              JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
                                                          maksuera m
                                                     WHERE tpi.urakka = ur
                                                       AND m.toimenpideinstanssi = tpi.id
                                                       AND tpk2.koodi = '23150'
                                                     limit 1);

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
                                     FROM kulu l
                                              JOIN kulu_kohdistus lk ON lk.kulu = l.id
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
                                       FROM kulu l
                                                JOIN kulu_kohdistus lk ON lk.kulu = l.id
                                                JOIN toimenpideinstanssi tpi
                                                     ON lk.toimenpideinstanssi = tpi.id AND tpi.id = t.tpi
                                       WHERE lk.maksueratyyppi != 'lisatyo' -- TODO: Sisältää kiinteähintaiset, kustannusarvioidut ja yksikkohintaiset työt
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
                                           FROM sanktion_indeksikorotus(perintapvm,
                                                                        indeksi,
                                                                        -maara,
                                                                        ur,
                                                                        sakkoryhma))                      AS indeksikorotettuna
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
            muu_bonus_laskutettu := 0.0;
            muu_bonus_laskutetaan := 0.0;
            tavoitehinnan_ulk_rahav_laskutettu := 0.0;
            tavoitehinnan_ulk_rahav_laskutetaan := 0.0;
            bonukset_laskutettu := 0.0;
            bonukset_laskutetaan := 0.0;

            hj_palkkio_laskutettu := 0.0;
            hj_palkkio_laskutetaan := 0.0;
            johto_ja_hallinto_laskutettu := 0.0;
            johto_ja_hallinto_laskutetaan := 0.0;
            hj_erillishankinnat_laskutettu := 0.0;
            hj_erillishankinnat_laskutetaan := 0.0;
            hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu := 0.0;
            hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan := 0.0;
            hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu := 0.0;
            hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan := 0.0;
            hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu := 0.0;
            hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan := 0.0;

            -- Hoidonjohdolla (toimenpidekoodi 23150) omat erilliset mahdolliset kulunsa.
            -- Erilliskustannus-tauluun tallennetaan erilaiset bonukset.
            -- Hoitokauden päättämiseen liittyviä kuluja ei tallenneta erilliskustannus tauluun, ne kirjataan kuluina ja tallennetaan kulu ja kulu_kohdistus-tauluun
            IF (t.tuotekoodi = '23150') THEN
                FOR erilliskustannus_rivi IN SELECT ek.pvm, ek.rahasumma, ek.indeksin_nimi, ek.tyyppi, ek.urakka
                                                 FROM erilliskustannus ek
                                                 WHERE ek.sopimus = sopimus_id
                                                   AND ek.toimenpideinstanssi = t.tpi
                                                   AND ek.pvm >= hk_alkupvm
                                                   AND ek.pvm <= aikavali_loppupvm
                                                   AND ek.poistettu IS NOT TRUE
                    LOOP

                        RAISE NOTICE ' ********************************************* ERILLISKUSTANNUS Tyyppi = % ', erilliskustannus_rivi.tyyppi;

                        -- Alihankintabonukselle ei tule indeksikorotusta, joten tämä saa jäädä näin.
                        -- Kun on aikaa, niin tämän voisi laittaa käyttämään erilliskustannuksen_indeksilaskentaa, vaikka korotusta ei tulisikaan
                        -- Mutta se olisi yhdenmukaisempaa
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
                            FROM erilliskustannuksen_indeksilaskenta(erilliskustannus_rivi.pvm,
                                                                     erilliskustannus_rivi.indeksin_nimi,
                                                                     erilliskustannus_rivi.rahasumma,
                                                                     erilliskustannus_rivi.urakka,
                                                                     erilliskustannus_rivi.tyyppi,
                                                                     pyorista_kerroin)
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
                                FROM erilliskustannuksen_indeksilaskenta(erilliskustannus_rivi.pvm,
                                                                         erilliskustannus_rivi.indeksin_nimi,
                                                                         erilliskustannus_rivi.rahasumma,
                                                                         erilliskustannus_rivi.urakka,
                                                                         erilliskustannus_rivi.tyyppi,
                                                                         pyorista_kerroin)
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

                            -- Muu bonus
                        ELSEIF erilliskustannus_rivi.tyyppi = 'muu-bonus' THEN
                            SELECT *
                              FROM erilliskustannuksen_indeksilaskenta(erilliskustannus_rivi.pvm,
                                                                       erilliskustannus_rivi.indeksin_nimi,
                                                                       erilliskustannus_rivi.rahasumma,
                                                                       erilliskustannus_rivi.urakka,
                                                                       erilliskustannus_rivi.tyyppi,
                                                                       pyorista_kerroin)
                              INTO muu_bonus_rivi;

                            IF erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                                -- Hoitokauden alusta
                                muu_bonus_laskutettu := muu_bonus_laskutettu +
                                                               COALESCE(muu_bonus_rivi.korotettuna, 0.0);

                                IF erilliskustannus_rivi.pvm >= aikavali_alkupvm AND
                                   erilliskustannus_rivi.pvm <= aikavali_loppupvm THEN
                                    -- Laskutetaan nyt
                                    muu_bonus_laskutetaan := muu_bonus_laskutetaan +
                                                                    COALESCE(muu_bonus_rivi.korotettuna, 0.0);
                                END IF;
                            END IF;

                        -- Tavoitepalkkio kirjataan kulujen kautta. Poistettu tavoitepalkkiokäsittely bonuksista.
                        END IF;
                    END LOOP;

                -- Laskentaan Johto- ja hallintakorvaus tehtäväryhmän rivit bonuksiksi, jos ne on lisätty kustannusarvioitu_työ tauluun
                -- ja jos niillä on yksilöivä_tunniste 'a6614475-1950-4a61-82c6-fda0fd19bb54'
                SELECT SUM(kt.summa) AS summa
                FROM kustannusarvioitu_tyo kt,
                     sopimus s
                WHERE kt.sopimus = sopimus_id
                  AND kt.toimenpideinstanssi = johto_ja_hallintakorvaus_toimenpideinstanssi_id
                  AND kt.tehtava IS NULL
                  -- Tämä kovakoodattu tehtäväryhmä on nimeltään - Johto- ja hallintokorvaus (J). Se on päätetty
                  -- tulkita Bonuksien alle tulevaksi Tilaajan varaukseksi Kustannusten suunnittelu sivulla, koska sen toimenpideinstanssin
                  -- id on 23150.
                  -- Tehtäväryhmä: Johto- ja hallintokorvaus (J) = 'a6614475-1950-4a61-82c6-fda0fd19bb54'
                  AND kt.tehtavaryhma =
                      (select id
                       from tehtavaryhma tr
                       where tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54')
                  AND kt.sopimus = s.id
                  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN hk_alkupvm::DATE AND hk_loppupvm::DATE)
                INTO tavoitehinnan_ulk_rahav_rivi;
                tavoitehinnan_ulk_rahav_laskutettu := COALESCE(tavoitehinnan_ulk_rahav_rivi.summa, 0.0);

                -- Laskutettu - eli valitun kuukauden summa
                SELECT SUM(kt.summa) AS summa
                FROM kustannusarvioitu_tyo kt,
                     sopimus s
                WHERE kt.sopimus = sopimus_id
                  AND kt.toimenpideinstanssi = johto_ja_hallintakorvaus_toimenpideinstanssi_id
                  AND kt.tehtava IS NULL
                  -- Tämä kovakoodattu tehtäväryhmä on nimeltään - Johto- ja hallintokorvaus (J). Se on päätetty
                  -- tulkita Bonuksien alle tulevaksi Tilaajan varaukseksi Kustannusten suunnittelu sivulla, koska sen toimenpideinstanssin
                  -- id on 23150.
                  -- Tehtäväryhmä: Johto- ja hallintokorvaus (J) = 'a6614475-1950-4a61-82c6-fda0fd19bb54'
                  AND kt.tehtavaryhma =
                      (select id
                       from tehtavaryhma tr
                       where tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54')
                  AND kt.sopimus = s.id
                  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN aikavali_alkupvm::DATE AND aikavali_loppupvm::DATE)
                INTO tavoitehinnan_ulk_rahav_rivi;
                tavoitehinnan_ulk_rahav_laskutetaan := COALESCE(tavoitehinnan_ulk_rahav_rivi.summa, 0.0);

                RAISE NOTICE 'Tavoitehinnan ulkopuoliset rahavaraukset laskutettu bonukseksi: % :: %', tavoitehinnan_ulk_rahav_laskutettu, tavoitehinnan_ulk_rahav_laskutetaan;
                RAISE NOTICE 'Alihankintabonus laskutettu :: laskutetaan: % :: %', alihank_bon_laskutettu, alihank_bon_laskutetaan;
                RAISE NOTICE 'Lupausbonus laskutettu :: laskutetaan: % :: %', lupaus_bon_laskutettu, lupaus_bon_laskutetaan;
                RAISE NOTICE 'Asiakastyytyväisyysbonus laskutettu :: laskutetaan: % :: %', asiakas_tyyt_bon_laskutettu, asiakas_tyyt_bon_laskutetaan;
                RAISE NOTICE 'Tavoitepalkkio laskutettu :: laskutetaan: % :: %', tavoitepalkk_bon_laskutettu, tavoitepalkk_bon_laskutetaan;

                bonukset_laskutettu := bonukset_laskutettu + alihank_bon_laskutettu + lupaus_bon_laskutettu +
                                       asiakas_tyyt_bon_laskutettu + tavoitepalkk_bon_laskutettu + muu_bonus_laskutettu
                                           + tavoitehinnan_ulk_rahav_laskutettu;
                bonukset_laskutetaan := bonukset_laskutetaan + alihank_bon_laskutetaan + lupaus_bon_laskutetaan +
                                        asiakas_tyyt_bon_laskutetaan + tavoitepalkk_bon_laskutetaan + muu_bonus_laskutetaan
                                            + tavoitehinnan_ulk_rahav_laskutetaan;
                RAISE NOTICE 'Bonuksia laskutettu :: laskutetaan: % :: %', bonukset_laskutettu, bonukset_laskutetaan;

                -- HOIDON JOHTO, tpk 23150.
                -- Hoidon johdon kustannukset eli johto- ja hallintokorvays, erillishankinnat ja hoidonjohtopalkkio lasketaan maksuerään suoraan
                -- kustannussuunnitelmasta. Suunniteltu rahasumma siirtyy maksuerään kuukauden viimeisenä päivänä.
                -- Poikkeustapauksissa hoidon johdon kustannuksia kirjataan kulujen kohdistuksessa. Tällöin kustannukset lasketaan mukaan samaan tapaan kuin
                -- muutkin hankinnat (ks. kohdistetut_laskutetaan alla).
                h_rivi := (SELECT hoidon_johto_yhteenveto(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm, t.tuotekoodi, t.tpi, ur, sopimus_id));

                johto_ja_hallinto_laskutettu := h_rivi.johto_ja_hallinto_laskutettu;
                johto_ja_hallinto_laskutetaan := h_rivi.johto_ja_hallinto_laskutetaan;

                -- HOIDONJOHTO --  HJ-Palkkio
                hj_palkkio_rivi :=
                        (SELECT hj_palkkio(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm, t.tuotekoodi, t.tpi, ur,
                                           sopimus_id));
                hj_palkkio_laskutettu := hj_palkkio_rivi.hj_palkkio_laskutettu;
                hj_palkkio_laskutetaan := hj_palkkio_rivi.hj_palkkio_laskutetaan;

                -- HOIDONJOHTO --  erillishankinnat
                hj_erillishankinnat_rivi :=
                        (SELECT hj_erillishankinnat(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm, t.tuotekoodi,
                                                    t.tpi, ur, sopimus_id));

                hj_erillishankinnat_laskutettu := hj_erillishankinnat_rivi.hj_erillishankinnat_laskutettu;
                hj_erillishankinnat_laskutetaan := hj_erillishankinnat_rivi.hj_erillishankinnat_laskutetaan;

                -- HOIDONJOHTO -- hoitovuoden päättämiseen liittyvät kulut: tavoitepalkkio, tavoitehinnan ylittäminen ja kattohinnan ylittäminen
                hj_hoitovuoden_paattaminen_rivi :=
                        (SELECT hj_hoitovuoden_paattaminen(hk_alkupvm, aikavali_alkupvm, aikavali_loppupvm, t.tuotekoodi,
                                                    t.tpi, ur, sopimus_id));

                hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu := hj_hoitovuoden_paattaminen_rivi.hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu;
                hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan := hj_hoitovuoden_paattaminen_rivi.hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan;
                hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu := hj_hoitovuoden_paattaminen_rivi.hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu;
                hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan := hj_hoitovuoden_paattaminen_rivi.hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan;
                hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu := hj_hoitovuoden_paattaminen_rivi.hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu;
                hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan := hj_hoitovuoden_paattaminen_rivi.hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan;

            END IF;
            -- Kustannusten kokonaissummat
            kaikki_laskutettu := 0.0;
            kaikki_laskutetaan := 0.0;
            kaikki_laskutettu := sakot_laskutettu + bonukset_laskutettu +
                                 hankinnat_laskutettu + lisatyot_laskutettu + johto_ja_hallinto_laskutettu +
                                 hj_palkkio_laskutettu + hj_erillishankinnat_laskutettu + hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu +
                                 hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu + hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu;

            kaikki_laskutetaan := sakot_laskutetaan + bonukset_laskutetaan +
                                  hankinnat_laskutetaan + lisatyot_laskutetaan + johto_ja_hallinto_laskutetaan +
                                  hj_palkkio_laskutetaan + hj_erillishankinnat_laskutetaan + hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan +
                                  hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan + hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan;

            -- Tavoitehintaan sisältyy: Hankinnat, Johto- ja Hallintokorvaukset, (hoidonjohto tässä), Erillishankinnat, HJ-Palkkio.
            -- Tavoitehintaan ei sisälly: Lisätyöt, Sanktiot, Suolasanktiot, Bonukset, Hoitovuoden päättämiseen liittyvät kulut.
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
            RAISE NOTICE 'Johto- ja Hallintokorvaus laskutettu: %', johto_ja_hallinto_laskutettu;
            RAISE NOTICE 'Erillishankinnat laskutettu: %', hj_erillishankinnat_laskutettu;
            RAISE NOTICE 'HJ-Palkkio laskutettu: %', hj_palkkio_laskutettu;
            RAISE NOTICE 'Bonukset laskutettu: %', bonukset_laskutettu;
            RAISE NOTICE 'Hoitovuoden päättäminen (tavoitepalkkio) laskutettu: %', hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu;
            RAISE NOTICE 'Hoitovuoden päättäminen (tavoitehinnan ylitys) laskutettu: %', hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu;
            RAISE NOTICE 'Hoitovuoden päättäminen (kattohinnan ylitys) laskutettu: %', hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu;


            RAISE NOTICE '
LASKUTETAAN AIKAVÄLILLÄ % - %:', aikavali_alkupvm, aikavali_loppupvm;
            RAISE NOTICE 'Lisatyot laskutetaan: %', lisatyot_laskutetaan;
            RAISE NOTICE 'Hankinnat laskutetaan: %', hankinnat_laskutetaan;
            RAISE NOTICE 'Sakot laskutetaan: %', sakot_laskutetaan;
            RAISE NOTICE 'Johto- ja hallintokorvaus laskutetaan: %', johto_ja_hallinto_laskutetaan;
            RAISE NOTICE 'Erillishankinnat laskutetaan: %', hj_erillishankinnat_laskutetaan;
            RAISE NOTICE 'HJ-Palkkio laskutetaan: %', hj_palkkio_laskutetaan;
            RAISE NOTICE 'Bonukset laskutetaan: %', bonukset_laskutetaan;
            RAISE NOTICE 'Hoitovuoden päättäminen (tavoitepalkkio) laskutetaan: %', hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan;
            RAISE NOTICE 'Hoitovuoden päättäminen (tavoitehinnan ylitys) laskutetaan: %', hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan;
            RAISE NOTICE 'Hoitovuoden päättäminen (kattohinnan ylitys) laskutetaan: %', hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan;

            RAISE NOTICE 'Kaikki laskutettu: %', kaikki_laskutettu;
            RAISE NOTICE 'Kaikki laskutetaan: %', kaikki_laskutetaan;

            RAISE NOTICE 'Tavoitehintaiset laskutettu: %', tavoitehintaiset_laskutettu;
            RAISE NOTICE 'Tavoitehintaiset laskutetaan: %', tavoitehintaiset_laskutetaan;


            RAISE NOTICE '********************************** Käsitelly loppui toimenpiteelle: %  *************************************
    ', t.nimi;

            rivi := (t.nimi, t.maksuera_numero, t.tuotekoodi, t.tpi, perusluku,
                     kaikki_laskutettu, kaikki_laskutetaan,
                     tavoitehintaiset_laskutettu, tavoitehintaiset_laskutetaan,
                     lisatyot_laskutettu, lisatyot_laskutetaan,
                     hankinnat_laskutettu, hankinnat_laskutetaan,
                     sakot_laskutettu, sakot_laskutetaan,
                     johto_ja_hallinto_laskutettu, johto_ja_hallinto_laskutetaan,
                     bonukset_laskutettu, bonukset_laskutetaan,
                     hj_palkkio_laskutettu, hj_palkkio_laskutetaan,
                     hj_erillishankinnat_laskutettu, hj_erillishankinnat_laskutetaan,
                     hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu, hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan,
                     hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu, hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan,
                     hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu, hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan,
                     indeksi_puuttuu
                );

            RETURN NEXT rivi;
        END LOOP;
END;
$$;
