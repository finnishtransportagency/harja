INSERT INTO tavoitehinnan_oikaisu
("urakka-id", "luoja-id", luotu, "muokkaaja-id", muokattu, otsikko, selite, hoitokausi, summa, poistettu)
VALUES ((SELECT id FROM urakka WHERE sampoid = '1242141-OULU3'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        'Oikaisu',
        'Muokattava testioikaisu',
        1,
        20000,
        false);

INSERT INTO tavoitehinnan_oikaisu
("urakka-id", "luoja-id", luotu, "muokkaaja-id", muokattu, otsikko, selite, hoitokausi, summa, poistettu)
VALUES ((SELECT id FROM urakka WHERE sampoid = '1242141-OULU3'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        'Oikaisu',
        'Poistettava testioikaisu',
        1,
        1234,
        false);