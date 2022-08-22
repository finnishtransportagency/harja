-- Tiemerkinnän muut työt

INSERT INTO urakka_laskentakohde (urakka, nimi, luotu, luoja)
VALUES
 ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022'),
  'Laskentakohde 1', NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh')),

 ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022'),
  'Laskentakohde 2', NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh')),

 ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022'),
  'Laskentakohde 3', NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh')),

 ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022'),
  'Laskentakohde 4', NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh'));

INSERT INTO yllapito_muu_toteuma (urakka, sopimus, selite, pvm, hinta, yllapitoluokka, laskentakohde, luotu, luoja)
VALUES
((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022'),
 (SELECT id FROM sopimus WHERE nimi = 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2013-2022'),
 'Selite 1',
 '2016-10-10',
 900,
 1,
 (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),
 NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh')),
((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022'),
 (SELECT id FROM sopimus WHERE nimi = 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2013-2022'),
 'Selite 2',
 '2016-10-11',
 500,
 2,
 (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),
 NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh')),
((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022'),
 (SELECT id FROM sopimus WHERE nimi = 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2013-2022'),
 'Selite 3',
 '2016-10-12',
 1200,
 2,
 (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 2'),
  NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh')),
 ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022'),
  (SELECT id FROM sopimus WHERE nimi = 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2013-2022'),
  'Selite 4',
  '2017-01-12',
  2400,
  2,
  (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 4'),
  NOW(), (SELECT id FROM kayttaja where kayttajanimi = 'jvh'));

-- Tiemerkinnän yks. hint. tyot

INSERT INTO tiemerkinnan_yksikkohintainen_toteuma
(urakka, yllapitokohde, hinta, hintatyyppi, paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus)
VALUES ((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022'),
null, 666, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', null, 'Testitoteuma 1', 20, 8, 5),
((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022'),
null, 123, 'suunnitelma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', null, 'Testitoteuma 2', 20, 9, 15),
((SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022'),
(SELECT id FROM yllapitokohde WHERE suorittava_tiemerkintaurakka = (SELECT id FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2013-2022') LIMIT 1),
500, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', '20 / 1 / 0 / 3 / 0', 'Testitoteuma 3', null, null, null);
