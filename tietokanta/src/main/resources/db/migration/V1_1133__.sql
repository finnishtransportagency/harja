-- Kustannussuunnitelman johto ja hallintokorvaukset vaativat toimenkuvat '2. työnjohtaja' ja '3. työnjohtaja'.
-- jotta niitä voidaan tallentaa kantaan.
INSERT INTO johto_ja_hallintokorvaus_toimenkuva (toimenkuva)
VALUES ('2. työnjohtaja');
INSERT INTO johto_ja_hallintokorvaus_toimenkuva (toimenkuva)
VALUES ('3. työnjohtaja');

-- Koska on mahdollista, että osa urakoista on jo laittanut suunniteltuja arvoja
-- "päätoiminen apulainen" ja "apulainen/työnjohtaja" toimenkuville, niin siirretään ne.
DO
$$
    DECLARE
        toimenkuva_paatoiminen_id  INTEGER;
        toimenkuva_apulainen_id    INTEGER;
        toimenkuva_tyonjohtaja2_id INTEGER;
        toimenkuva_tyonjohtaja3_id INTEGER;
        ur                         RECORD;

    BEGIN
        SELECT id
          INTO toimenkuva_paatoiminen_id
          FROM johto_ja_hallintokorvaus_toimenkuva
         WHERE toimenkuva = 'päätoiminen apulainen';
        SELECT id
          INTO toimenkuva_apulainen_id
          FROM johto_ja_hallintokorvaus_toimenkuva
         WHERE toimenkuva = 'apulainen/työnjohtaja';
        SELECT id
          INTO toimenkuva_tyonjohtaja2_id
          FROM johto_ja_hallintokorvaus_toimenkuva
         WHERE toimenkuva = '2. työnjohtaja';
        SELECT id
          INTO toimenkuva_tyonjohtaja3_id
          FROM johto_ja_hallintokorvaus_toimenkuva
         WHERE toimenkuva = '3. työnjohtaja';

        -- Haetaan 2024 alkavat MH-urakat ja tehdään muutos vain heille
        FOR ur IN SELECT id FROM urakka WHERE EXTRACT(YEAR FROM alkupvm) = 2024 AND tyyppi = 'teiden-hoito'
            LOOP
                UPDATE johto_ja_hallintokorvaus
                   SET "toimenkuva-id" = toimenkuva_tyonjohtaja2_id
                 WHERE "toimenkuva-id" = toimenkuva_paatoiminen_id
                   AND "urakka-id" = ur.id;
                UPDATE johto_ja_hallintokorvaus
                   SET "toimenkuva-id" = toimenkuva_tyonjohtaja3_id
                 WHERE "toimenkuva-id" = toimenkuva_apulainen_id
                   AND "urakka-id" = ur.id;

            END LOOP;
    END
$$;
