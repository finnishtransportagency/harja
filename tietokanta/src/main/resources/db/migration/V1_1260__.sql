-- Partitioidaan toteuma_tehtava ja toteuma_materiaali -taulut, jotta toteumahaut nopeutuisivat

--  Luodaan uusi partitioitu taulu toteuma-tehtavalle. Uuden taulun rakenne on sama kuin vanhan, mutta se on partitioitu hoitovuosittain.
CREATE TABLE toteuma_tehtava_part (
                                      id                    SERIAL,
                                      toteuma               INTEGER NOT NULL,
                                      toimenpidekoodi       INTEGER NOT NULL,
                                      maara                 NUMERIC,
                                      paivan_hinta          NUMERIC,
                                      lisatieto             VARCHAR(4096),
                                      poistettu             BOOLEAN NOT NULL DEFAULT FALSE,
                                      indeksi               BOOLEAN          DEFAULT TRUE,
                                      urakka_id             INTEGER,
                                      hoitokauden_alkuvuosi INTEGER NOT NULL, -- Partitioavain
                                      luoja                 INTEGER REFERENCES kayttaja (id),
                                      luotu                 TIMESTAMP        DEFAULT current_timestamp,
                                      muokkaaja             INTEGER REFERENCES kayttaja (id),
                                      muokattu              TIMESTAMP,
                                      PRIMARY KEY (id, hoitokauden_alkuvuosi)
) PARTITION BY RANGE (hoitokauden_alkuvuosi); -- Partitioidaan siis hoitovuosittain

-- Luodaan partitiot hoitovuosittain
CREATE TABLE toteuma_tehtava_hk_0000 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (MINVALUE) TO (2014); -- kaikki ennen 2014

CREATE TABLE toteuma_tehtava_hk_2014 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2014) TO (2015);

CREATE TABLE toteuma_tehtava_hk_2015 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2015) TO (2016);

CREATE TABLE toteuma_tehtava_hk_2016 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2016) TO (2017);

CREATE TABLE toteuma_tehtava_hk_2017 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2017) TO (2018);

CREATE TABLE toteuma_tehtava_hk_2018 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2018) TO (2019);

CREATE TABLE toteuma_tehtava_hk_2019 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2019) TO (2020);

CREATE TABLE toteuma_tehtava_hk_2020 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2020) TO (2021);

CREATE TABLE toteuma_tehtava_hk_2021 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2021) TO (2022);

CREATE TABLE toteuma_tehtava_hk_2022 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2022) TO (2023);

CREATE TABLE toteuma_tehtava_hk_2023 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2023) TO (2024);

CREATE TABLE toteuma_tehtava_hk_2024 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2024) TO (2025);

CREATE TABLE toteuma_tehtava_hk_2025 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2025) TO (2026);

CREATE TABLE toteuma_tehtava_hk_2026 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2026) TO (2027);

CREATE TABLE toteuma_tehtava_hk_2027 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2027) TO (2028);

CREATE TABLE toteuma_tehtava_hk_2028 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2028) TO (2029);

CREATE TABLE toteuma_tehtava_hk_2029 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2029) TO (2030);

CREATE TABLE toteuma_tehtava_hk_2030 PARTITION OF toteuma_tehtava_part
    FOR VALUES FROM (2030) TO (MAXVALUE);

-- Tehdään funktio, jolla siirretään olemassa olevat toteuma_tehtavat uuteen partitioituun tauluun.
-- Funktiokutsu on raskas operaatio, joka tehdään useammassa erässä.
CREATE OR REPLACE PROCEDURE siirra_toteuma_tehtava_partitioihin(
    erakoko INTEGER DEFAULT 10000,
    aloitus_id BIGINT DEFAULT NULL,
    lopetus_id BIGINT DEFAULT NULL
)
    LANGUAGE plpgsql
AS
$$
DECLARE
    min_id             BIGINT;
    max_id             BIGINT;
    nykyinen_id        BIGINT;
    todellinen_max     BIGINT;
    eran_rivit         INTEGER;
    siirretty_yhteensa BIGINT  := 0;
    kokonaismaara      BIGINT;
    aloitus            TIMESTAMP;
    eranumero          INTEGER := 0;
BEGIN
    SELECT MIN(id), MAX(id), COUNT(*) INTO min_id, max_id, kokonaismaara FROM toteuma_tehtava;
    nykyinen_id := COALESCE(aloitus_id, min_id);
    todellinen_max := COALESCE(lopetus_id, max_id);
    aloitus := clock_timestamp();

    RAISE NOTICE '=== Aloitetaan siirto ===';
    RAISE NOTICE 'ID-alue taulussa: % - %, ajetaan: % - %, eräkoko: %', min_id, max_id, nykyinen_id, todellinen_max, erakoko;

    LOOP
        eranumero := eranumero + 1;

        INSERT INTO toteuma_tehtava_part (id, toteuma, toimenpidekoodi, maara, paivan_hinta,
                                          lisatieto, poistettu, indeksi, urakka_id,
                                          hoitokauden_alkuvuosi, luoja, luotu, muokkaaja, muokattu)
        SELECT tt.id,
               tt.toteuma,
               tt.toimenpidekoodi,
               tt.maara,
               tt.paivan_hinta,
               tt.lisatieto,
               tt.poistettu,
               tt.indeksi,
               tt.urakka_id,
               CASE
                   WHEN EXTRACT(MONTH FROM t.alkanut) >= 10
                       THEN EXTRACT(YEAR FROM t.alkanut)::INTEGER
                   ELSE EXTRACT(YEAR FROM t.alkanut)::INTEGER - 1
                   END,
               tt.luoja,
               tt.luotu,
               tt.muokkaaja,
               tt.muokattu
        FROM toteuma_tehtava tt
                 JOIN toteuma t ON tt.toteuma = t.id
        WHERE tt.id >= nykyinen_id
          AND tt.id < nykyinen_id + erakoko
        ON CONFLICT do NOTHING;

        GET DIAGNOSTICS eran_rivit = ROW_COUNT;
        siirretty_yhteensa := siirretty_yhteensa + eran_rivit;
        nykyinen_id := nykyinen_id + erakoko;

        RAISE NOTICE 'Erä #%: id % - %, rivit: % | Yhteensä: %',
            eranumero,
            nykyinen_id - erakoko,
            nykyinen_id - 1,
            eran_rivit,
            siirretty_yhteensa;

        EXIT WHEN nykyinen_id > todellinen_max;
    END LOOP;

    RAISE NOTICE '=== Siirto valmis === Rivejä: %, aika: % s',
        siirretty_yhteensa, EXTRACT(EPOCH FROM clock_timestamp() - aloitus)::INTEGER;
END;
$$;

-- Esimerkki siirtokutsusta
-- CALL siirra_toteuma_tehtava_partitioihin(100000, 1, 100000);

-- toteuma_materiaali taululle sama hoitovuosittainen partitointi
CREATE TABLE toteuma_materiaali_part (
                                         id                    SERIAL,
                                         toteuma               INTEGER NOT NULL,
                                         materiaalikoodi       INTEGER REFERENCES materiaalikoodi (id),
                                         maara                 NUMERIC,
                                         poistettu             BOOLEAN NOT NULL DEFAULT FALSE,
                                         urakka_id             INTEGER,
                                         hoitokauden_alkuvuosi INTEGER NOT NULL,
                                         luoja                 INTEGER REFERENCES kayttaja (id),
                                         luotu                 TIMESTAMP,
                                         muokkaaja             INTEGER REFERENCES kayttaja (id),
                                         muokattu              TIMESTAMP,
                                         PRIMARY KEY (id, hoitokauden_alkuvuosi)
) PARTITION BY RANGE (hoitokauden_alkuvuosi);

-- Luodaan partitiot hoitovuosittain
CREATE TABLE toteuma_materiaali_hk_0000 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (MINVALUE) TO (2014);

CREATE TABLE toteuma_materiaali_hk_2014 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2014) TO (2015);

CREATE TABLE toteuma_materiaali_hk_2015 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2015) TO (2016);

CREATE TABLE toteuma_materiaali_hk_2016 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2016) TO (2017);

CREATE TABLE toteuma_materiaali_hk_2017 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2017) TO (2018);

CREATE TABLE toteuma_materiaali_hk_2018 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2018) TO (2019);

CREATE TABLE toteuma_materiaali_hk_2019 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2019) TO (2020);

CREATE TABLE toteuma_materiaali_hk_2020 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2020) TO (2021);

CREATE TABLE toteuma_materiaali_hk_2021 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2021) TO (2022);

CREATE TABLE toteuma_materiaali_hk_2022 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2022) TO (2023);

CREATE TABLE toteuma_materiaali_hk_2023 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2023) TO (2024);

CREATE TABLE toteuma_materiaali_hk_2024 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2024) TO (2025);

CREATE TABLE toteuma_materiaali_hk_2025 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2025) TO (2026);

CREATE TABLE toteuma_materiaali_hk_2026 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2026) TO (2027);

CREATE TABLE toteuma_materiaali_hk_2027 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2027) TO (2028);

CREATE TABLE toteuma_materiaali_hk_2028 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2028) TO (2029);

CREATE TABLE toteuma_materiaali_hk_2029 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2029) TO (2030);

CREATE TABLE toteuma_materiaali_hk_2030 PARTITION OF toteuma_materiaali_part
    FOR VALUES FROM (2030) TO (MAXVALUE);

-- Funktio, jolla data siirretään vanhasta taulusta uuteen partitioituun tauluun. Ajetaan useammassa erässä, koska operaatio on raskas.
CREATE OR REPLACE PROCEDURE siirra_toteuma_materiaali_partitioihin(
    erakoko INTEGER DEFAULT 10000,
    aloitus_id BIGINT DEFAULT NULL,
    lopetus_id BIGINT DEFAULT NULL
)
    LANGUAGE plpgsql
AS
$$
DECLARE
    min_id             BIGINT;
    max_id             BIGINT;
    nykyinen_id        BIGINT;
    todellinen_max     BIGINT;
    eran_rivit         INTEGER;
    siirretty_yhteensa BIGINT  := 0;
    kokonaismaara      BIGINT;
    aloitus            TIMESTAMP;
    eranumero          INTEGER := 0;
BEGIN
    SELECT MIN(id), MAX(id), COUNT(*)
    INTO min_id, max_id, kokonaismaara
    FROM toteuma_materiaali;

    nykyinen_id := COALESCE(aloitus_id, min_id);
    todellinen_max := COALESCE(lopetus_id, max_id);
    aloitus := clock_timestamp();

    RAISE NOTICE '=== Aloitetaan siirto ===';
    RAISE NOTICE 'ID-alue taulussa: % - %, ajetaan: % - %, eräkoko: %',
        min_id, max_id, nykyinen_id, todellinen_max, erakoko;

    LOOP
        eranumero := eranumero + 1;

        INSERT INTO toteuma_materiaali_part (id, toteuma, materiaalikoodi, maara,
                                             poistettu, urakka_id, hoitokauden_alkuvuosi,
                                             luoja,luotu,
                                             muokkaaja, muokattu)
        SELECT tm.id,
               tm.toteuma,
               tm.materiaalikoodi,
               tm.maara,
               tm.poistettu,
               tm.urakka_id,
               CASE
                   WHEN EXTRACT(MONTH FROM t.alkanut) >= 10
                       THEN EXTRACT(YEAR FROM t.alkanut)::INTEGER
                   ELSE EXTRACT(YEAR FROM t.alkanut)::INTEGER - 1
                   END,
               tm.luoja,
               tm.luotu,
               tm.muokkaaja,
               tm.muokattu
        FROM toteuma_materiaali tm
                 JOIN toteuma t ON tm.toteuma = t.id
        WHERE tm.id >= nykyinen_id
          AND tm.id < nykyinen_id + erakoko
        ON CONFLICT DO NOTHING;

        GET DIAGNOSTICS eran_rivit = ROW_COUNT;
        siirretty_yhteensa := siirretty_yhteensa + eran_rivit;
        nykyinen_id := nykyinen_id + erakoko;

        RAISE NOTICE 'Erä #%: id % - %, rivit: % | Yhteensä: %',
            eranumero,
            nykyinen_id - erakoko,
            nykyinen_id - 1,
            eran_rivit,
            siirretty_yhteensa;

        EXIT WHEN nykyinen_id > todellinen_max;
    END LOOP;

    RAISE NOTICE '=== Siirto valmis === Rivejä: %, aika: % s',
        siirretty_yhteensa,
        EXTRACT(EPOCH FROM clock_timestamp() - aloitus)::INTEGER;
END;
$$;

-- Esimerkki siirtokutsusta
-- CALL siirra_toteuma_materiaali_partitioihin(100000, 1, 100000);

-- Indeksit toteuma_tehtava (luodaan jokaiselle partitiolle automaattisesti)
CREATE INDEX ON toteuma_tehtava_part (urakka_id, poistettu);
CREATE INDEX ON toteuma_tehtava_part (toimenpidekoodi);
CREATE INDEX ON toteuma_tehtava_part (toteuma);
CREATE INDEX toteuma_tehtava_urakka_hk_idx
    ON toteuma_materiaali_part (urakka_id, hoitokauden_alkuvuosi)
    WHERE poistettu = FALSE;

-- Indeksit toteuma_materiaali (luodaan jokaiselle partitiolle automaattisesti)
CREATE INDEX ON toteuma_materiaali_part (urakka_id, poistettu);
CREATE INDEX ON toteuma_materiaali_part (materiaalikoodi);
CREATE INDEX ON toteuma_materiaali_part (toteuma);
CREATE INDEX toteuma_materiaali_urakka_hk_idx
    ON toteuma_materiaali_part (urakka_id, hoitokauden_alkuvuosi)
    WHERE poistettu = FALSE;

-- Triggeri-funktio: synkronoi toteuma_tehtava -> toteuma_tehtava_part
-- Tätä käytetään sen aikaa, kunnes toteuma_tehtava_part taulu on vaihdettu toteuma_tehtava taulun tilalle (ja uudelleen nimetty)
-- Uudelleen nimeäminen tehdään erillisessä migraatiossa. Ja koska datan siirto on raskas operaatio,
-- halutaan varmistaa, että uuteen tauluun siirtyy kaikki toteuma_tehtava -tauluun tehdyt muutokset siirron aikana.
CREATE OR REPLACE FUNCTION synkronoi_toteuma_tehtava_partitioihin()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    hk_alkuvuosi INTEGER;
BEGIN
    SELECT CASE
               WHEN EXTRACT(MONTH FROM t.alkanut) >= 10
                   THEN EXTRACT(YEAR FROM t.alkanut)::INTEGER
               ELSE EXTRACT(YEAR FROM t.alkanut)::INTEGER - 1
               END
      INTO hk_alkuvuosi
      FROM toteuma t
     WHERE t.id = NEW.toteuma;

    IF TG_OP = 'INSERT' THEN
        INSERT INTO toteuma_tehtava_part (id, toteuma, toimenpidekoodi, maara, paivan_hinta,
                                          lisatieto, poistettu, indeksi, urakka_id,
                                          hoitokauden_alkuvuosi, luoja, luotu, muokkaaja, muokattu)
        VALUES (NEW.id, NEW.toteuma, NEW.toimenpidekoodi, NEW.maara, NEW.paivan_hinta,
                NEW.lisatieto, NEW.poistettu, NEW.indeksi, NEW.urakka_id,
                hk_alkuvuosi, NEW.luoja, NEW.luotu, NEW.muokkaaja, NEW.muokattu)
        ON CONFLICT (id, hoitokauden_alkuvuosi) DO UPDATE
            SET toteuma         = EXCLUDED.toteuma,
                toimenpidekoodi = EXCLUDED.toimenpidekoodi,
                maara           = EXCLUDED.maara,
                paivan_hinta    = EXCLUDED.paivan_hinta,
                lisatieto       = EXCLUDED.lisatieto,
                poistettu       = EXCLUDED.poistettu,
                indeksi         = EXCLUDED.indeksi,
                urakka_id       = EXCLUDED.urakka_id,
                muokkaaja       = EXCLUDED.muokkaaja,
                muokattu        = EXCLUDED.muokattu;

    ELSIF TG_OP = 'UPDATE' THEN
        -- Jos toteuma on vaihtunut, hoitokauden_alkuvuosi voi muuttua -> poistetaan vanha rivi
        IF OLD.toteuma IS DISTINCT FROM NEW.toteuma THEN
            DELETE FROM toteuma_tehtava_part
             WHERE id = OLD.id;
        END IF;

        INSERT INTO toteuma_tehtava_part (id, toteuma, toimenpidekoodi, maara, paivan_hinta,
                                          lisatieto, poistettu, indeksi, urakka_id,
                                          hoitokauden_alkuvuosi, luoja, luotu, muokkaaja, muokattu)
        VALUES (NEW.id, NEW.toteuma, NEW.toimenpidekoodi, NEW.maara, NEW.paivan_hinta,
                NEW.lisatieto, NEW.poistettu, NEW.indeksi, NEW.urakka_id,
                hk_alkuvuosi, NEW.luoja, NEW.luotu, NEW.muokkaaja, NEW.muokattu)
        ON CONFLICT (id, hoitokauden_alkuvuosi) DO UPDATE
            SET toteuma         = EXCLUDED.toteuma,
                toimenpidekoodi = EXCLUDED.toimenpidekoodi,
                maara           = EXCLUDED.maara,
                paivan_hinta    = EXCLUDED.paivan_hinta,
                lisatieto       = EXCLUDED.lisatieto,
                poistettu       = EXCLUDED.poistettu,
                indeksi         = EXCLUDED.indeksi,
                urakka_id       = EXCLUDED.urakka_id,
                muokkaaja       = EXCLUDED.muokkaaja,
                muokattu        = EXCLUDED.muokattu;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER toteuma_tehtava_synkronoi_partitioihin
    AFTER INSERT OR UPDATE
    ON toteuma_tehtava
    FOR EACH ROW
EXECUTE FUNCTION synkronoi_toteuma_tehtava_partitioihin();

-- Triggeri-funktio: synkronoi toteuma_materiaali -> toteuma_materiaali_part
CREATE OR REPLACE FUNCTION synkronoi_toteuma_materiaali_partitioihin()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    hk_alkuvuosi INTEGER;
BEGIN
    SELECT CASE
               WHEN EXTRACT(MONTH FROM t.alkanut) >= 10
                   THEN EXTRACT(YEAR FROM t.alkanut)::INTEGER
               ELSE EXTRACT(YEAR FROM t.alkanut)::INTEGER - 1
               END
      INTO hk_alkuvuosi
      FROM toteuma t
     WHERE t.id = NEW.toteuma;

    IF TG_OP = 'INSERT' THEN
        INSERT INTO toteuma_materiaali_part (id, toteuma, materiaalikoodi, maara,
                                             poistettu, urakka_id, hoitokauden_alkuvuosi,
                                             luoja, luotu, muokkaaja, muokattu)
        VALUES (NEW.id, NEW.toteuma, NEW.materiaalikoodi, NEW.maara,
                NEW.poistettu, NEW.urakka_id, hk_alkuvuosi,
                NEW.luoja, NEW.luotu, NEW.muokkaaja, NEW.muokattu)
        ON CONFLICT (id, hoitokauden_alkuvuosi) DO UPDATE
            SET toteuma         = EXCLUDED.toteuma,
                materiaalikoodi = EXCLUDED.materiaalikoodi,
                maara           = EXCLUDED.maara,
                poistettu       = EXCLUDED.poistettu,
                urakka_id       = EXCLUDED.urakka_id,
                muokkaaja       = EXCLUDED.muokkaaja,
                muokattu        = EXCLUDED.muokattu;

    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.toteuma IS DISTINCT FROM NEW.toteuma THEN
            DELETE FROM toteuma_materiaali_part
             WHERE id = OLD.id;
        END IF;

        INSERT INTO toteuma_materiaali_part (id, toteuma, materiaalikoodi, maara,
                                             poistettu, urakka_id, hoitokauden_alkuvuosi,
                                             luoja, luotu, muokkaaja, muokattu)
        VALUES (NEW.id, NEW.toteuma, NEW.materiaalikoodi, NEW.maara,
                NEW.poistettu, NEW.urakka_id, hk_alkuvuosi,
                NEW.luoja, NEW.luotu, NEW.muokkaaja, NEW.muokattu)
        ON CONFLICT (id, hoitokauden_alkuvuosi) DO UPDATE
            SET toteuma         = EXCLUDED.toteuma,
                materiaalikoodi = EXCLUDED.materiaalikoodi,
                maara           = EXCLUDED.maara,
                poistettu       = EXCLUDED.poistettu,
                urakka_id       = EXCLUDED.urakka_id,
                muokkaaja       = EXCLUDED.muokkaaja,
                muokattu        = EXCLUDED.muokattu;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER toteuma_materiaali_synkronoi_partitioihin
    AFTER INSERT OR UPDATE
    ON toteuma_materiaali
    FOR EACH ROW
EXECUTE FUNCTION synkronoi_toteuma_materiaali_partitioihin();
