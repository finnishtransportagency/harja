CREATE OR REPLACE FUNCTION laske_tr_tiedot(tie_ INTEGER, osa_ INTEGER)
    RETURNS JSONB
AS $$
DECLARE
    tulos                     JSONB;
    counter                   INTEGER;
    osan_pituus               INTEGER;
    osan_alkuetaisyys         INTEGER;

    osoitteet_pointer           TEXT [];
    edelliset_ajoradan_tiedot RECORD;
    ajoradan_tiedot           RECORD;
BEGIN
    SELECT NULL
    INTO edelliset_ajoradan_tiedot;

    osan_pituus = (SELECT sum("tr-loppuetaisyys" - "tr-alkuetaisyys") AS pituus
                   FROM tr_osoitteet
                   WHERE "tr-numero" = tie_ AND "tr-osa" = osa_
                     -- Tiputetaan 2 ajorata pois, jottei samalta pätkältä lasketa pituutta tuplana
                     AND "tr-ajorata" IN (0, 1)
                     AND "tr-kaista" IN (1, 11, 31) -- 31 = kaksisuuntainen ajorata
                   GROUP BY "tr-numero", "tr-osa");
    osan_alkuetaisyys = (SELECT min("tr-alkuetaisyys")
                         FROM tr_osoitteet
                         WHERE "tr-numero" = tie_ AND "tr-osa" = osa_
                         GROUP BY "tr-numero", "tr-osa");
    -- Asetetaan osan pituus sekä alkuetaisyys. Alkuetäisyys ei ole aina 0.
    tulos = jsonb_build_object('pituus', osan_pituus, 'tr-alkuetaisyys', osan_alkuetaisyys, 'osoitteet', jsonb_build_array());
    counter = 0;
    FOR ajoradan_tiedot IN (SELECT
                                "tr-ajorata",
                                "tr-kaista",
                                "tr-alkuetaisyys",
                                "tr-loppuetaisyys" - "tr-alkuetaisyys" AS pituus
                            FROM tr_osoitteet
                            WHERE "tr-numero" = tie_ AND "tr-osa" = osa_
                            ORDER BY "tr-ajorata" ASC, "tr-kaista" ASC, "tr-alkuetaisyys" ASC)
        LOOP
            osoitteet_pointer = ('{"osoitteet", ' || counter || '}') :: TEXT [];
            tulos = jsonb_set(tulos,
                              osoitteet_pointer :: TEXT [],
                              jsonb_build_object('tr-ajorata', ajoradan_tiedot."tr-ajorata",
                                                 'tr-kaista', ajoradan_tiedot."tr-kaista",
                                                 'pituus', ajoradan_tiedot.pituus,
                                                 'tr-alkuetaisyys', ajoradan_tiedot."tr-alkuetaisyys"));
            counter = counter + 1;
        END LOOP;
    RETURN tulos;
END;
$$ LANGUAGE plpgsql;

