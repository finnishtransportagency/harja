DO $$
    DECLARE
        urakkaid INTEGER;
        kayttajaid INTEGER;
        alkuvuosi INTEGER := 2021;

    BEGIN
        urakkaid = (SELECT id FROM urakka where nimi = 'Oulun MHU 2019-2024');
        kayttajaid = (SELECT id FROM kayttaja where kayttajanimi = 'yit_uuvh');

        INSERT INTO lupaus_sitoutuminen ("urakka-id", pisteet, luoja)
        VALUES (urakkaid, 76, kayttajaid);

        INSERT INTO lupaus_vastaus ("lupaus-id", "urakka-id", kuukausi, vuosi, vastaus, "lupaus-vaihtoehto-id", luoja)
        VALUES ((SELECT id FROM lupaus WHERE jarjestys = 1 AND "urakan-alkuvuosi" = alkuvuosi),
                urakkaid, 10, alkuvuosi, TRUE, NULL, kayttajaid),
               ((SELECT id FROM lupaus WHERE jarjestys = 2 AND "urakan-alkuvuosi" = alkuvuosi),
                urakkaid, 10, alkuvuosi, TRUE, NULL, kayttajaid);
    END
$$ LANGUAGE plpgsql;