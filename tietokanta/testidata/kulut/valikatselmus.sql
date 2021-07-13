INSERT INTO tavoitehinnan_oikaisu
("urakka-id", "luoja-id", luotu, "muokkaaja-id", muokattu, otsikko, selite, "hoitokauden-alkuvuosi", summa, poistettu)
VALUES ((SELECT id FROM urakka WHERE sampoid = '1242141-OULU3'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        'Oikaisu',
        'Muokattava testioikaisu',
        2020,
        20000,
        false);

INSERT INTO tavoitehinnan_oikaisu
("urakka-id", "luoja-id", luotu, "muokkaaja-id", muokattu, otsikko, selite, "hoitokauden-alkuvuosi", summa, poistettu)
VALUES ((SELECT id FROM urakka WHERE sampoid = '1242141-OULU3'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        'Oikaisu',
        'Poistettava testioikaisu',
        2020,
        1234,
        false);

INSERT INTO tavoitehinnan_oikaisu
("urakka-id", "luoja-id", luotu, "muokkaaja-id", muokattu, otsikko, selite, "hoitokauden-alkuvuosi", summa, poistettu)
VALUES ((SELECT id FROM urakka WHERE sampoid = '1242141-OULU3'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        'Oikaisu',
        'Oikaistaan tavoitehintaa',
        2020,
        1234,
        false),
    ((SELECT id FROM urakka WHERE sampoid = '1242141-OULU3'),
     (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
     NOW(),
     (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
     NOW(),
     'Oikaisu',
     'Oikaistaan tavoitehintaa lisää',
     2020,
     8766,
     false);

