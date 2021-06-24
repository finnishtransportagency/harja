DO $$
    DECLARE
        urakkaid INTEGER;
        kayttajaid INTEGER;

    BEGIN
        urakkaid = (SELECT id FROM urakka where nimi = 'Oulun MHU 2019-2024');
        kayttajaid = (SELECT id FROM kayttaja where kayttajanimi = 'yit_uuvh');

        INSERT INTO lupaus_sitoutuminen ("urakka-id", pisteet, luoja)
        VALUES (urakkaid, 76, kayttajaid);
    END
$$ LANGUAGE plpgsql;