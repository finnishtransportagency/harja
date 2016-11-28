-- Oulun alueurakka 2005-2012

INSERT INTO urakka_laskentakohde (urakka, nimi)
VALUES ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
        'Laskentakohde 1'),
        ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
        'Laskentakohde 2'),
        ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
        'Laskentakohde 3');

INSERT INTO yllapito_toteuma (urakka, selite, pvm, hinta, yllapitoluokka, laskentakohde)
VALUES
((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
 'Selite 1',
 '2016-10-10',
 900,
 1,
 (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1')),
((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
 'Selite 2',
 '2016-10-11',
 500,
 2,
 (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1')),
((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
 'Selite 3',
 '2016-10-12',
 1200,
 2,
 (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 2'))