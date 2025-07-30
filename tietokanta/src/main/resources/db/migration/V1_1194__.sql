-- Estä saman toimenkuvan lisääminen useaan kertaan
CREATE UNIQUE INDEX johto_ja_hallintokorvaus_toimenkuva_urakka_id_index
    on johto_ja_hallintokorvaus_toimenkuva (toimenkuva, "urakka-id");

-- Lisätään toimenkuvalle urakkakohtainen nimi
ALTER TABLE johto_ja_hallintokorvaus_toimenkuva
    ADD COLUMN IF NOT EXISTS urakkakohtainen_nimi VARCHAR(255) NULL;

-- Lisätään urakkakohtaiset toimenkuvat kaikille urakoille
-- Toimenkuvat riippuvat urakan alkuvuodesta
CREATE OR REPLACE FUNCTION lisaa_toimenkuvat_urakalle(urakan_alkupvm DATE) RETURNS VOID AS $$
DECLARE
    urakkaid INTEGER;
BEGIN

    -- Haetaan vain mh-urakat
    FOR urakkaid IN SELECT id FROM urakka WHERE alkupvm = urakan_alkupvm::DATE AND tyyppi = 'teiden-hoito'
        LOOP

            RAISE NOTICE 'Lisätään toimenkuvat urakalle: % ', urakkaid;
            -- Tämä funktio voidaan ajaa monesti, joten tarkistetaan ensin, onko toimenkuva jo lisätty
            IF EXISTS (SELECT 1 FROM johto_ja_hallintokorvaus_toimenkuva
                       WHERE "urakka-id" = urakkaid) THEN
                RAISE NOTICE 'Toimenkuvat on jo lisätty urakalle: %', urakkaid;
                CONTINUE; -- Jatketaan seuraavaan urakkaan, jos toimenkuvat on jo lisätty
            END IF;

            -- 2019, 2020, 2021 urakoille
            -- Näillä on vanhassa kustiksessa erikoisuutena se, että päätoiminen apulainen ja apulainen/tyonjohtaja
            -- on jaettu kesäkauteen ja talvikauteen ja UI näyttää ne erikseen. Tietokannassa ne on vain yhtenä rivinä.
            CASE WHEN urakan_alkupvm IN ('2019-10-01'::DATE, '2020-10-01'::DATE, '2021-10-01'::DATE) THEN
                RAISE NOTICE 'Lisätään toimenkuvat 2019, 2020, 2021 urakalle: % alkupäivä: %', urakkaid, urakan_alkupvm;
                INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id", toimenkuva, urakkakohtainen_nimi)
                VALUES (urakkaid, 'sopimusvastaava', 'Sopimusvastaava'),
                       (urakkaid, 'vastuunalainen työnjohtaja', 'Vastuunalainen työnjohtaja'),
                       (urakkaid, 'päätoiminen apulainen', 'Päätoiminen apulainen'),
                       (urakkaid, 'apulainen/työnjohtaja', 'Apulainen/työnjohtaja'),
                       (urakkaid, 'viherhoidosta vastaava henkilö', 'Viherhoidosta vastaava henkilö'),
                       (urakkaid, 'hankintavastaava', 'Hankintavastaava'),
                       (urakkaid, 'harjoittelija', 'Harjoittelija');
                 ELSE RAISE NOTICE 'Ei osu alkupäivään 2019, 2020, 2021 alkupäivä: %', urakan_alkupvm;
                END CASE;
            -- 2022, 2023 urakoille
            CASE WHEN urakan_alkupvm IN ('2022-10-01'::DATE, '2023-10-01'::DATE) THEN

                RAISE NOTICE 'Lisätään toimenkuvat 2022, 2023 urakalle: % alkupäivä: %', urakkaid, urakan_alkupvm;
                INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id", toimenkuva, urakkakohtainen_nimi)
                VALUES (urakkaid, 'valmistelukausi ennen urakka-ajan alkua', 'Valmistelukausi ennen urakka-ajan alkua'),
                       (urakkaid, 'vastuunalainen työnjohtaja', 'Vastuunalainen työnjohtaja'),
                       (urakkaid, 'päätoiminen apulainen', 'Päätoiminen apulainen'),
                       (urakkaid, 'apulainen/työnjohtaja', 'Apulainen/työnjohtaja'),
                       (urakkaid, 'viherhoidosta vastaava henkilö', 'Viherhoidosta vastaava henkilö'),
                       (urakkaid, 'hankintavastaava', 'Hankintavastaava'),
                       (urakkaid, 'harjoittelija', 'Harjoittelija');
                 ELSE RAISE NOTICE 'Ei osu alkupäivään 2022, 2023 alkupäivä: %', urakan_alkupvm;
                END CASE;

            -- 2024 - alkaen
            CASE WHEN urakan_alkupvm > '2024-09-30'::DATE THEN
                RAISE NOTICE 'Lisätään toimenkuvat >= 2024 urakalle: %, urakan_alkupvm: %', urakkaid, urakan_alkupvm;
                INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id", toimenkuva, urakkakohtainen_nimi)
                VALUES (urakkaid, 'valmistelukausi ennen urakka-ajan alkua', 'Valmistelukausi ennen urakka-ajan alkua'),
                       (urakkaid, 'vastuunalainen työnjohtaja', 'Vastuunalainen työnjohtaja'),
                       (urakkaid, '2. työnjohtaja', '2. Työnjohtaja'),
                       (urakkaid, '3. työnjohtaja', '3. Työnjohtaja'),
                       (urakkaid, 'viherhoidosta vastaava henkilö', 'Viherhoidosta vastaava henkilö'),
                       (urakkaid, 'hankintavastaava', 'Hankintavastaava'),
                       (urakkaid, 'harjoittelija', 'Harjoittelija');
                 ELSE RAISE NOTICE 'Ei osu alkupäivään >= 2024 alkupäivä: %', urakan_alkupvm;
                END CASE;

        END LOOP;
END
$$ LANGUAGE plpgsql;

SELECT lisaa_toimenkuvat_urakalle('2019-10-01'::DATE);
SELECT lisaa_toimenkuvat_urakalle('2020-10-01'::DATE);
SELECT lisaa_toimenkuvat_urakalle('2021-10-01'::DATE);
SELECT lisaa_toimenkuvat_urakalle('2022-10-01'::DATE);
SELECT lisaa_toimenkuvat_urakalle('2023-10-01'::DATE);
SELECT lisaa_toimenkuvat_urakalle('2024-10-01'::DATE);
SELECT lisaa_toimenkuvat_urakalle('2025-10-01'::DATE);
