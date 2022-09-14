DO
$$
    DECLARE
        _kayttaja INTEGER;
        _urakka   INTEGER;
        _luotu    TIMESTAMP;
    BEGIN
        _kayttaja := (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh');
        _urakka := (SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024');
        _luotu := '2022-07-11 15:00:18.947853';

        -- Pohjavesialue: Hanko
        INSERT INTO rajoitusalue(id, tierekisteriosoite, sijainti, pituus, ajoratojen_pituus, urakka_id, luotu, luoja)
        VALUES (1, ROW (25, 2, 200, 3, 2837, NULL)::TR_OSOITE,
                (select * from tierekisteriosoitteelle_viiva(25, 2, 200, 3, 2837) as sijainti),
                NULL, NULL, _urakka, _luotu, _kayttaja);


        INSERT INTO rajoitusalue_rajoitus(rajoitusalue_id, suolarajoitus, formiaatti, hoitokauden_alkuvuosi, luotu,
                                          luoja)
        VALUES (1, 6.6, FALSE, 2020, _luotu, _kayttaja);
        INSERT INTO rajoitusalue_rajoitus(rajoitusalue_id, suolarajoitus, formiaatti, hoitokauden_alkuvuosi, luotu,
                                          luoja)
        VALUES (1, 6.6, FALSE, 2021, _luotu, _kayttaja);
        INSERT INTO rajoitusalue_rajoitus(rajoitusalue_id, suolarajoitus, formiaatti, hoitokauden_alkuvuosi, luotu,
                                          luoja)
        VALUES (1, 6.6, FALSE, 2022, _luotu, _kayttaja);
        INSERT INTO rajoitusalue_rajoitus(rajoitusalue_id, suolarajoitus, formiaatti, hoitokauden_alkuvuosi, luotu,
                                          luoja)
        VALUES (1, 7.0, FALSE, 2023, _luotu, _kayttaja);
        INSERT INTO rajoitusalue_rajoitus(rajoitusalue_id, suolarajoitus, formiaatti, hoitokauden_alkuvuosi, luotu,
                                          luoja)
        VALUES (1, 7.0, FALSE, 2024, _luotu, _kayttaja);
        ---


        -- FIXME: Ei voi lisätä, koska tällä hetkellä rajoitusalue constraint 'tierekisteriosoite_ei_leikkaa' ei salli
        --       sellaisen tierekisteriosoitteen lisäämistä, joka alkaa suoraan edellisen osoitteen loppuosasta.
        -- Pohjavesialue: Sandö-Grönvik
--         INSERT INTO rajoitusalue(id, tierekisteriosoite, pituus, ajoratojen_pituus, urakka, luotu, luoja)
--         VALUES (2, ROW (25, 3, 2837, 5, 1153, NULL)::TR_OSOITE, NULL, NULL, _urakka, _luotu, _kayttaja);
--
--         INSERT INTO rajoitusalue_rajoitus(id, rajoitusalue_id, suolarajoitus, formiaatti, hoitokauden_alkuvuosi,
--         luotu, luoja)
--         VALUES (6, 2, 6.6, FALSE, 2022, _luotu, _kayttaja);

    END
$$;

-- Jostain syystä rajoitusalueen id:n serial sequenssi menee sekaisin, niin päivitetään se kuntoon samalla
SELECT SETVAL('public.rajoitusalue_id_seq', (SELECT nextval('public.rajoitusalue_id_seq') + 1));

