-- Luodaan vesiväyläurakka, siihen liittyvä sopimus ja toimenpideinstanssit
-- TODO: Sopimuksesta puuttuu oikea sampoid, koska hanketta ei ole vielä luotu

-- URAKKA
INSERT INTO urakka (sampoid, nimi, tyyppi, hallintayksikko, alkupvm, loppupvm)
VALUES
  ('PR00008543', 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL', 'hoito', 2, '2016-08-01', '2017-07-30');

-- SOPIMUS
INSERT INTO sopimus (nimi, sampoid, urakka, paasopimus, alkupvm, loppupvm)
VALUES ('Helsingin väyläyksikön pääsopimus', 'XXXsampoid', (SELECT id
                                                            FROM urakka
                                                            WHERE sampoid = 'PR00008543'), NULL,
        '2016-08-01', '2017-07-30');

-- TOIMENPIDEKOODIT
INSERT INTO toimenpidekoodi (taso, emo, nimi)
VALUES (3, 132, 'Rannikon kauppamerenkulku');
INSERT INTO toimenpidekoodi (taso, emo, nimi)
VALUES (3, 132, 'Rannikon muut');

-- TOIMENPIDEINSTANSSIT
INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide, sampoid, alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE sampoid = 'PR00008543'),
        'Väylänhoito, Itäinen Suomenlahti, sopimuksen kok.hintaiset työt, rann kmrk, TP',
        (SELECT id
         FROM toimenpidekoodi
         WHERE koodi = '24104'), 'PR00008551', '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide, sampoid, alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE sampoid = 'PR00008543'),
        'Väylänhoito, Itäinen Suomenlahti, sopimuksen kok.hintaiset työt, rann muu vl, TP',
        (SELECT id
         FROM toimenpidekoodi
         WHERE nimi = 'Rannikon muut'), 'PR00008502', '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide, sampoid, alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE sampoid = 'PR00008543'),
        'Väylänhoito, Itäinen Suomenlahti,erikseen tilattavat työt, rann kmrk, TP',
        (SELECT id
         FROM toimenpidekoodi
         WHERE nimi = 'Rannikon kauppamerenkulku'), 'PR00008553', '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide, sampoid, alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE sampoid = 'PR00008543'),
        'Väylänhoito, Itäinen Suomenlahti,erikseen tilattavat työt, rann muu vl, TP',
        (SELECT id
         FROM toimenpidekoodi
         WHERE koodi = '24103'), 'PR00008511', '2016-08-01', '2017-07-30');

-- Reimarista tuodut toimenpiteet

INSERT INTO reimari_toimenpide ("reimari-id", urakoitsija, sopimus, turvalaite, lisatieto, lisatyo, suoritettu, luotu, "reimari-luotu", alus, tila, tyyppi, tyolaji, tyoluokka, vayla, turvalaite) values (42, '(23, Ukko Urakoitsija)', '(-5, 1022542301, Hoitosopimus)' , '(62, Pysäytin, 555)', '', false, '2017-01-01T23:23Z', '2017-01-01', '2017-01-01', '(MBKE24524, MS Piggy)', '1022541202', '1022542001', '1022541802', 1022541905, '(123, Vayla X, 55)', );
