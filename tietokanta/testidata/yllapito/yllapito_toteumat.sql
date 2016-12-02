INSERT INTO urakka_laskentakohde (urakka, nimi, luotu, luoja)
VALUES
 ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
  'Laskentakohde 1', NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh')),

 ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
  'Laskentakohde 2', NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh')),

 ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
  'Laskentakohde 3', NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh')),

 ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
  'Laskentakohde 4', NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh'));

INSERT INTO yllapito_toteuma (urakka, sopimus, selite, pvm, hinta, yllapitoluokka, laskentakohde, luotu, luoja)
VALUES
((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
 (SELECT id FROM sopimus WHERE nimi = 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2013-2018'),
 'Selite 1',
 '2016-10-10',
 900,
 1,
 (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),
 NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh')),
((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
 (SELECT id FROM sopimus WHERE nimi = 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2013-2018'),
 'Selite 2',
 '2016-10-11',
 500,
 2,
 (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),
 NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh')),
((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
 (SELECT id FROM sopimus WHERE nimi = 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2013-2018'),
 'Selite 3',
 '2016-10-12',
 1200,
 2,
 (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 2'),
  NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh')),
 ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2018'),
  (SELECT id FROM sopimus WHERE nimi = 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2013-2018'),
  'Selite 4',
  '2017-01-12',
  2400,
  2,
  (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 4'),
  NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh'))