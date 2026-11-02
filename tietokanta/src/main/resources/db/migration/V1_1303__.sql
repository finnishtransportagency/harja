-- Lisätään vuodesta 2023 alkaen alkaville vesiväylä-hoito-urakoille
-- urakkakohtaiset johto- ja hallintokorvauksen toimenkuvat.
CREATE OR REPLACE FUNCTION lisaa_vesivaylaurakan_toimenkuvat_urakalle(urakan_alkupvm DATE) RETURNS VOID AS $$
DECLARE
    urakkaid INTEGER;
BEGIN

FOR urakkaid IN
SELECT id FROM urakka WHERE alkupvm = urakan_alkupvm AND tyyppi = 'vesivayla-hoito' AND alkupvm >= DATE '2023-01-01'
    LOOP
        INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id",toimenkuva,urakkakohtainen_nimi)
        VALUES (urakkaid,'valmistelukausi ennen urakka-ajan alkua','Valmistelukausi ennen urakka-ajan alkua')
        ON CONFLICT DO NOTHING;

        INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id",toimenkuva,urakkakohtainen_nimi)
        VALUES (urakkaid,'sopimusvastaava','Sopimusvastaava')
        ON CONFLICT DO NOTHING;

        INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id",toimenkuva,urakkakohtainen_nimi)
        VALUES (urakkaid,'vastuunalainen työnjohtaja','Vastuunalainen työnjohtaja')
        ON CONFLICT DO NOTHING;

        INSERT INTO johto_ja_hallintokorvaus_toimenkuva ("urakka-id",toimenkuva,urakkakohtainen_nimi)
        VALUES (urakkaid,'2. työnjohtaja','2. työnjohtaja')
        ON CONFLICT DO NOTHING;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

SELECT lisaa_vesivaylaurakan_toimenkuvat_urakalle('2023-01-01'::DATE);
