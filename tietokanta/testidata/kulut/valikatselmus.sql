INSERT INTO tavoitehinnan_oikaisu
("urakka-id", "luoja-id", luotu, "muokkaaja-id", muokattu, otsikko, selite, "hoitokauden-alkuvuosi", summa, poistettu)
VALUES ((SELECT id FROM urakka WHERE sampoid = '1242141-II3'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        'Oikaisu',
        'Muokattava testioikaisu',
        2021,
        20000,
        false);

INSERT INTO tavoitehinnan_oikaisu
("urakka-id", "luoja-id", luotu, "muokkaaja-id", muokattu, otsikko, selite, "hoitokauden-alkuvuosi", summa, poistettu)
VALUES ((SELECT id FROM urakka WHERE sampoid = '1242141-II3'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        'Oikaisu',
        'Poistettava testioikaisu',
        2021,
        1234,
        false);

INSERT INTO tavoitehinnan_oikaisu
("urakka-id", "luoja-id", luotu, "muokkaaja-id", muokattu, otsikko, selite, "hoitokauden-alkuvuosi", summa, poistettu)
VALUES ((SELECT id FROM urakka WHERE sampoid = '1242141-II3'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
        NOW(),
        'Oikaisu',
        'Oikaistaan tavoitehintaa',
        2021,
        1234,
        false),
    ((SELECT id FROM urakka WHERE sampoid = '1242141-II3'),
     (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
     NOW(),
     (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'),
     NOW(),
     'Oikaisu',
     'Oikaistaan tavoitehintaa lisää',
     2021,
     8766,
     false);

