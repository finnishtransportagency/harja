INSERT INTO paikkauskohde ("luoja-id",
                           "ulkoinen-id",
                           nimi)
VALUES ((SELECT id
         FROM kayttaja
         WHERE kayttajanimi = 'destia'
         LIMIT 1),
        666,
        'Testikohde');

INSERT INTO paikkaustoteuma ("luoja-id",
                             luotu,
                             "muokkaaja-id",
                             muokattu,
                             "poistaja-id",
                             poistettu,
                             "urakka-id",
                             "paikkauskohde-id",
                             "ulkoinen-id",
                             alkuaika,
                             loppuaika,
                             tierekisteriosoite,
                             tyomenetelma,
                             massatyyppi,
                             leveys,
                             massamenekki,
                             raekoko,
                             kuulamylly) VALUES (
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'destia'
   LIMIT 1),
  '2018-02-06 12:47:24.183975',
  NULL,
  NULL,
  NULL,
  FALSE,
  4,
  (SELECT id
   FROM paikkauskohde
   WHERE "ulkoinen-id" = 666
   LIMIT 1),
  6661,
  '2018-02-06 12:47:24.183975',
  '2018-02-06 12:47:24.183975',
  ROW (20, 1, 1, 1, 100, NULL) :: TR_OSOITE,
  'massapintaus',
  'asfalttibetoni',
  1.3,
  2,
  1,
  '2');

INSERT INTO paikkauksen_materiaalit ("paikkaustoteuma-id",
                                     esiintyma,
                                     "kuulamylly-arvo",
                                     muotoarvo,
                                     sideainetyyppi,
                                     pitoisuus,
                                     "lisa-aineet")
VALUES ((SELECT id
         FROM paikkaustoteuma
         WHERE "ulkoinen-id" = 6661
         LIMIT 1),
        'Testikivi',
        '1',
        'Muotoarvo',
        'Sideaine',
        3.2,
        'Lisäaineet');

INSERT INTO paikkauksen_tienkohta ("paikkaustoteuma-id",
                                   ajorata,
                                   reunat,
                                   ajourat,
                                   ajouravalit,
                                   keskisaumat)
VALUES ((SELECT id
         FROM paikkaustoteuma
         WHERE "ulkoinen-id" = 6661
         LIMIT 1),
        1,
        ARRAY [0],
        ARRAY [1, 2],
        ARRAY [1],
        NULL);
