INSERT INTO toimenpideinstanssi (urakka,
                                 toimenpide,
                                 nimi,
                                 alkupvm,
                                 loppupvm)
VALUES (31,
        597,
        'Testitoimenpideinstanssi',
        '2017-01-01',
        '2090-01-01');

INSERT INTO kan_toimenpide
(tyyppi,
 urakka,
 sopimus,
 pvm,
 kohde,
 huoltokohde,
 toimenpidekoodi,
 lisatieto,
 suorittaja,
 kuittaaja,
 luotu,
 luoja,
 muokattu,
 muokkaaja,
 poistettu,
 poistaja)
VALUES ('kokonaishintainen' :: KAN_TOIMENPIDETYYPPI,
  (SELECT id
   FROM urakka
   WHERE nimi = 'Saimaan kanava'),
  (SELECT id
   FROM sopimus
   WHERE nimi = 'Saimaan huollon pääsopimus'),
  '2017-10-10',
  (SELECT id
   FROM kan_kohde
   WHERE nimi = 'Tikkalansaaren avattava ratasilta'),
  (SELECT id
   FROM kan_huoltokohde
   WHERE nimi = 'ASENNONMITTAUSLAITTEET'),
  (SELECT id
   FROM toimenpidekoodi
   WHERE emo = (SELECT id
                FROM toimenpidekoodi
                WHERE koodi = '24104') AND
         nimi = 'Ei yksilöity'),
  'Testitoimenpide',
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'jvh'),
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'jvh'),
  '2017-10-10',
        (SELECT id
         FROM kayttaja
         WHERE kayttajanimi = 'jvh'),
        '2017-10-10',
        (SELECT id
         FROM kayttaja
         WHERE kayttajanimi = 'jvh'),
        NULL,
        NULL);

INSERT INTO kan_toimenpide
(tyyppi,
 urakka,
 sopimus,
 pvm,
 kohde,
 huoltokohde,
 toimenpidekoodi,
 lisatieto,
 suorittaja,
 kuittaaja,
 luotu,
 luoja,
 muokattu,
 muokkaaja,
 poistettu,
 poistaja)
VALUES ('kokonaishintainen' :: KAN_TOIMENPIDETYYPPI,
  (SELECT id
   FROM urakka
   WHERE nimi = 'Saimaan kanava'),
  (SELECT id
   FROM sopimus
   WHERE nimi = 'Saimaan huollon pääsopimus'),
  '2016-11-07',
  (SELECT id
   FROM kan_kohde
   WHERE nimi = 'Taipaleen sulku'),
  (SELECT id
   FROM kan_huoltokohde
   WHERE nimi = 'ASENNONMITTAUSLAITTEET'),
  (SELECT id
   FROM toimenpidekoodi
   WHERE emo = (SELECT id
                FROM toimenpidekoodi
                WHERE koodi = '24104') AND
         nimi = 'Ei yksilöity'),
  'Testitoimenpide',
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'jvh'),
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'jvh'),
  '2016-11-07',
        (SELECT id
         FROM kayttaja
         WHERE kayttajanimi = 'jvh'),
        '2016-11-07',
        (SELECT id
         FROM kayttaja
         WHERE kayttajanimi = 'jvh'),
        NULL,
        NULL);

INSERT INTO kan_toimenpide
(tyyppi,
 urakka,
 sopimus,
 pvm,
 kohde,
 huoltokohde,
 toimenpidekoodi,
 lisatieto,
 suorittaja,
 kuittaaja,
 luotu,
 luoja,
 muokattu,
 muokkaaja,
 poistettu,
 poistaja)
VALUES ('kokonaishintainen' :: KAN_TOIMENPIDETYYPPI,
  (SELECT id
   FROM urakka
   WHERE nimi = 'Saimaan kanava'),
  (SELECT id
   FROM sopimus
   WHERE nimi = 'Saimaan huollon lisäsopimus'),
  '2017-01-07',
  (SELECT id
   FROM kan_kohde
   WHERE nimi = 'Tikkalansaaren avattava ratasilta'),
  (SELECT id
   FROM kan_huoltokohde
   WHERE nimi = 'ASENNONMITTAUSLAITTEET'),
  (SELECT id
   FROM toimenpidekoodi
   WHERE emo = (SELECT id
                FROM toimenpidekoodi
                WHERE koodi = '24104') AND
         nimi = 'Ei yksilöity'),
  'Testitoimenpide',
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'jvh'),
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'jvh'),
  '2017-01-07',
        (SELECT id
         FROM kayttaja
         WHERE kayttajanimi = 'jvh'),
        '2017-01-07',
        (SELECT id
         FROM kayttaja
         WHERE kayttajanimi = 'jvh'),
        NULL,
        NULL)