-- Estä saman toimenkuvan lisääminen useaan kertaan
CREATE UNIQUE INDEX johto_ja_hallintokorvaus_toimenkuva_urakka_id_index
    on johto_ja_hallintokorvaus_toimenkuva (toimenkuva, "urakka-id");

-- Tietokannassa on muutamia tyhjä string arvoja puutteellisen käyttöliittymän vuoksi.
-- Muokataan nämä arvot NULL arvoiksi. Niin seuraava unique index toimii oikein.
UPDATE johto_ja_hallintokorvaus_toimenkuva
SET toimenkuva = NULL
WHERE toimenkuva = '';

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

            RAISE NOTICE 'Lisätään toimenkuvat -25 urakalle: % ', urakkaid;
            -- 2025 - alkaen -- aiemmille ei voi lisätä, koska niillä on jo toimenkuvat ja ne on tulleet koodista
            CASE WHEN urakan_alkupvm > '2025-09-30'::DATE THEN
                RAISE NOTICE 'Lisätään toimenkuvat >= 2024 urakalle: %, urakan_alkupvm: %', urakkaid, urakan_alkupvm;
                INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id", toimenkuva, urakkakohtainen_nimi)
                VALUES (urakkaid, 'valmistelukausi ennen urakka-ajan alkua', 'Valmistelukausi ennen urakka-ajan alkua') ON CONFLICT DO NOTHING;
                INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id", toimenkuva, urakkakohtainen_nimi)
                VALUES      (urakkaid, 'vastuunalainen työnjohtaja', 'Vastuunalainen työnjohtaja') ON CONFLICT DO NOTHING;
                INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id", toimenkuva, urakkakohtainen_nimi)
                VALUES      (urakkaid, '2. työnjohtaja', '2. Työnjohtaja') ON CONFLICT DO NOTHING;
                INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id", toimenkuva, urakkakohtainen_nimi)
                VALUES       (urakkaid, '3. työnjohtaja', '3. Työnjohtaja') ON CONFLICT DO NOTHING;
                INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id", toimenkuva, urakkakohtainen_nimi)
                VALUES       (urakkaid, 'viherhoidosta vastaava henkilö', 'Viherhoidosta vastaava henkilö') ON CONFLICT DO NOTHING;
                INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id", toimenkuva, urakkakohtainen_nimi)
                VALUES       (urakkaid, 'hankintavastaava', 'Hankintavastaava') ON CONFLICT DO NOTHING;
                INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id", toimenkuva, urakkakohtainen_nimi)
                VALUES      (urakkaid, 'harjoittelija', 'Harjoittelija') ON CONFLICT DO NOTHING;
                 ELSE RAISE NOTICE 'Ei osu alkupäivään >= 2024 alkupäivä: %', urakan_alkupvm;
                END CASE;

        END LOOP;
END
$$ LANGUAGE plpgsql;

SELECT lisaa_toimenkuvat_urakalle('2025-10-01'::DATE);
