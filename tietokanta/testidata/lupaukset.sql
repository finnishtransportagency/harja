DO $$
    DECLARE
        urakkaid INTEGER;
        kayttajaid INTEGER;
        alkuvuosi INTEGER := 2021;

    BEGIN
        urakkaid = (SELECT id FROM urakka where nimi = 'Iin MHU 2021-2026');
        kayttajaid = (SELECT id FROM kayttaja where kayttajanimi = 'yit_uuvh');

        INSERT INTO lupaus_sitoutuminen ("urakka-id", pisteet, luoja)
        VALUES (urakkaid, 76, kayttajaid);

        INSERT INTO lupaus_vastaus ("lupaus-id", "urakka-id", kuukausi, vuosi, vastaus, "lupaus-vaihtoehto-id", luoja)
        VALUES ((SELECT id FROM lupaus WHERE jarjestys = 1 AND "urakan-alkuvuosi" = alkuvuosi),
                urakkaid, 10, alkuvuosi, TRUE, null, kayttajaid),
               ((SELECT id FROM lupaus WHERE jarjestys = 2 AND "urakan-alkuvuosi" = alkuvuosi),
                urakkaid, 10, alkuvuosi, TRUE, null, kayttajaid),
               ((SELECT id FROM lupaus WHERE jarjestys = 3 AND "urakan-alkuvuosi" = alkuvuosi),
                urakkaid, 10, alkuvuosi, TRUE, 4, kayttajaid),
               ((SELECT id FROM lupaus WHERE jarjestys = 3 AND "urakan-alkuvuosi" = alkuvuosi),
                urakkaid, 11, alkuvuosi, TRUE, 6, kayttajaid);
    END
$$ LANGUAGE plpgsql;