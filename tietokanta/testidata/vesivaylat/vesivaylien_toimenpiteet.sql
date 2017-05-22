-- ***********************************************
-- VIKA ILMAN KORJAUSTA
-- ***********************************************

INSERT INTO vv_vikailmoitus
("reimari-id", kuvaus, pvm, "turvalaite-id")
VALUES
  ('1234', 'Akonniemen kyltti on lähtenyt irti myrskyn takia', '2017-04-02', (SELECT id FROM vv_turvalaite WHERE nimi = 'Akonniemen kyltti'));

-- ***********************************************
-- KOKONAISHINTAISET TOIMENPITEET ILMAN VIKAA
-- ***********************************************

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
  "turvalaite-id",
 lisatieto,
 lisatyo,
 suoritettu,
 luotu,
  luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
"vayla-id")
VALUES
  ('kokonaishintainen',
    12,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus)',
    (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
    '(62, Hietasaaren poiju, 555)',
    (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren poiju'),
    '',
    FALSE,
    '2017-05-05T23:23Z',
    '2017-05-05',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
    '2017-05-05',
   '(MBKE24524, MS Piggy)',
   '1022541202',
   '1022542001',
   '1022541802',
   '1022541905',
   '(123, Hietasaaren läntinen rinnakkaisväylä, 55)',
   (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaite-id",
 lisatieto,
 lisatyo,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vayla-id")
VALUES
  ('kokonaishintainen',
    52,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus)',
    (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
    '(62, Hietasaaren poiju, 555)',
    (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren poiju'),
    '',
    FALSE,
    '2017-05-08T23:23Z',
    '2017-05-08',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
    '2017-05-08',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542049',
    '1022541802',
    '1022541903',
    '(123, Akonniemen väylät, 55)',
    (SELECT id FROM vv_vayla WHERE nimi = 'Akonniemen väylät'));

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaite-id",
 lisatieto,
 lisatyo,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vayla-id")
VALUES
  ('kokonaishintainen',
    62,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus)',
    (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
    '(62, Hietasaaren poiju, 555)',
    (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren poiju'),
    '',
    FALSE,
    '2017-05-08T23:23Z',
    '2017-05-08',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
    '2017-05-08',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542049',
    '1022541803',
    '1022541903',
    '(123, Akonniemen väylät, 55)',
    (SELECT id FROM vv_vayla WHERE nimi = 'Akonniemen väylät'));

-- ***********************************************
-- KOKONAISHINTAISET TOIMENPITEET VIALLA
-- ***********************************************

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaite-id",
 lisatieto,
 lisatyo,
 suoritettu,
 luotu,
  luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vayla-id")
VALUES
  ('kokonaishintainen',
    22,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus)',
    (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
    '(62, Hietasaaren viitta, 555)',
    (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren viitta'),
    'TESTITOIMENPIDE 2',
    FALSE,
    '2017-04-04T23:23Z',
   '2017-04-04',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-04-04',
   '(MBKE24524, MS Piggy)',
   '1022541202',
   '1022542001',
   '1022541802',
   '1022541905',
   '(123, Hietasaaren läntinen rinnakkaisväylä, 55)',
   (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

INSERT INTO vv_vikailmoitus
("reimari-id", kuvaus, pvm, "turvalaite-id", "toimenpide-id")
    VALUES
      ('123', 'Hietasaaren viitta on kaatunut', '2017-04-02', (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren viitta'),
        (SELECT id
         FROM reimari_toimenpide
         WHERE lisatieto = 'TESTITOIMENPIDE 2'));

-- ***********************************************
-- KOKONAISHINTAISIIN SIIRRETYT, REIMARISTA YKSIKKÖHINTAISENA RAPORTOIDUT TYÖT
-- ***********************************************

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaite-id",
 lisatieto,
 lisatyo,
 suoritettu,
 luotu,
  luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vayla-id")
VALUES
  ('kokonaishintainen',
    32,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus)',
    (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
    '(62, Hietasaaren poiju, 555)',
    (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren poiju'),
    '',
    TRUE,
    '2017-05-03T23:23Z',
   '2017-05-03',
   (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-05-03',
   '(MBKE24524, MS Piggy)',
   '1022541202',
   '1022542001',
   '1022541802',
   '1022541905',
   '(123, Hietasaaren läntinen rinnakkaisväylä, 55)',
   (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

-- ***********************************************
-- YKSIKKÖHINTAISIIN SIIRRETYT TYÖT, ILMAN HINTAERITTELYÄ
-- ***********************************************

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaite-id",
 lisatieto,
 lisatyo,
 suoritettu,
 luotu,
  luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vayla-id")
VALUES
  ('yksikkohintainen',
    42,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus)',
    (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
    '(62, Hietasaaren poiju, 555)',
    (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren poiju'),
    '',
    FALSE,
    '2017-05-08T23:23Z',
   '2017-05-08',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-05-08',
   '(MBKE24524, MS Piggy)',
   '1022541202',
   '1022542001',
   '1022541802',
   '1022541905',
   '(123, Hietasaaren läntinen rinnakkaisväylä, 55)',
   (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));


-- ***********************************************
-- TODO: YKSIKKÖHINTAISIIN SIIRRETTY TYÖ, HINTAERITTELY TEHTY
-- ***********************************************

-- ***********************************************
-- TODO: REIMARISTA YKSIKKÖHINTAISENA RAPORTOITU TYÖ
-- ***********************************************

-- ***********************************************
-- TODO: REIMARISTA YKSIKKÖHINTAISENA RAPORTOITU, KÖNTTÄSUMMALLA HINNOITELTU TOIMENPIDE
-- ***********************************************


-- ***********************************************
-- TODO: ERIKSEEN TILATTU YKSIKKÖHINTAINEN TYÖ?
-- ***********************************************