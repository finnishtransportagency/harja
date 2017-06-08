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
 <<<<<<< HEAD
 "urakka-id",
 =======
 "urakka-id",
 >>>>>>> HAR-5020_vesivayla_ykshint
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
    (SELECT id FROM urakka WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
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
 <<<<<<< HEAD
 "urakka-id",
 =======
 "urakka-id",
 >>>>>>> HAR-5020_vesivayla_ykshint
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
    (SELECT id FROM urakka WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    22,
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
   <<<<<<< HEAD
   (SELECT id FROM vv_vayla WHERE nimi = 'Akonniemen väylät'));

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
 =======
(SELECT id FROM vv_vayla WHERE nimi = 'Akonniemen väylät'));

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
 >>>>>>> HAR-5020_vesivayla_ykshint
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
    (SELECT id FROM urakka WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
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
   <<<<<<< HEAD
   (SELECT id FROM vv_vayla WHERE nimi = 'Akonniemen väylät'));
=======
(SELECT id FROM vv_vayla WHERE nimi = 'Akonniemen väylät'));
>>>>>>> HAR-5020_vesivayla_ykshint

-- ***********************************************
-- KOKONAISHINTAISET TOIMENPITEET VIALLA
-- ***********************************************

INSERT INTO reimari_toimenpide
(hintatyyppi,
 <<<<<<< HEAD
 "urakka-id",
 =======
 "urakka-id",
 >>>>>>> HAR-5020_vesivayla_ykshint
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
    (SELECT id FROM urakka WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    42,
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
<<<<<<< HEAD
VALUES
  ('123', 'Hietasaaren viitta on kaatunut', '2017-04-02', (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren viitta'),
   (SELECT id
    FROM reimari_toimenpide
    WHERE lisatieto = 'TESTITOIMENPIDE 2'));
=======
VALUES
  ('123', 'Hietasaaren viitta on kaatunut', '2017-04-02', (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren viitta'),
   (SELECT id
    FROM reimari_toimenpide
    WHERE lisatieto = 'TESTITOIMENPIDE 2'));
>>>>>>> HAR-5020_vesivayla_ykshint

-- ***********************************************
-- KOKONAISHINTAISIIN SIIRRETYT, REIMARISTA YKSIKKÖHINTAISENA RAPORTOIDUT TYÖT
-- ***********************************************

INSERT INTO reimari_toimenpide
(hintatyyppi,
 <<<<<<< HEAD
 "urakka-id",
 =======
 "urakka-id",
 >>>>>>> HAR-5020_vesivayla_ykshint
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
    (SELECT id FROM urakka WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
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
 <<<<<<< HEAD
 "urakka-id",
 =======
 "urakka-id",
 >>>>>>> HAR-5020_vesivayla_ykshint
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
    (SELECT id FROM urakka WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
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
    '1022542001',
    '1022541802',
    '1022541905',
    '(123, Hietasaaren läntinen rinnakkaisväylä, 55)',
   (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));


-- ***********************************************
-- TODO: YKSIKKÖHINTAISIIN SIIRRETTY TYÖ, HINTAERITTELY MATERIAALEIHIN/KOMPONENTTEIHIN/TÖIHIN TEHTY
-- ***********************************************


-- ***********************************************
-- TODO: REIMARISTA YKSIKKÖHINTAISENA RAPORTOITU TYÖ
-- ***********************************************

-- ***********************************************
<<<<<<< HEAD
-- TODO: REIMARISTA YKSIKKÖHINTAISENA RAPORTOITU, KÖNTTÄSUMMALLA HINNOITELTU TOIMENPIDE
=======
-- REIMARISTA YKSIKKÖHINTAISENA RAPORTOITU, KÖNTTÄSUMMALLA
-- SEKÄ TARKENNETULLA HINNALLA HINNOITELTU TOIMENPIDE
-- ***********************************************
>>>>>>> HAR-5020_vesivayla_ykshint

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
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
    (SELECT id FROM urakka WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    72,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus)',
    (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
    '(62, Hietasaaren pienempi poiju, 555)',
    (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren pienempi poiju'),
    'Poijujen korjausta kuten on sovittu',
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

<<<<<<< HEAD
INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
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
    (SELECT id FROM urakka WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    72,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus)',
    (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
    '(62, Hietasaaren pienempi poiju, 555)',
    (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren pienempi poiju'),
    'Poijujen korjausta taas',
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

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
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
    (SELECT id FROM urakka WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    72,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus)',
    (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
    '(62, Hietasaaren pienempi poiju, 555)',
    (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren pienempi poiju'),
    'Poijujen korjausta jossain ihan muualla',
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
   (SELECT id FROM vv_vayla WHERE nimi = 'Muu väylä'));

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
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
    (SELECT id FROM urakka WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    72,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus)',
    (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
    '(62, Hietasaaren pienempi poiju, 555)',
    (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren pienempi poiju'),
    'Yksittäisen poljun korjaus',
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
   (SELECT id FROM vv_vayla WHERE nimi = 'Muu väylä'));

INSERT INTO vv_hinnoittelu
(nimi, "urakka-id", hintaryhma, luoja)
VALUES
  ('Hietasaaren poijujen korjaus hintaryhmä', (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'), true, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO vv_hinnoittelu
(nimi, "urakka-id", hintaryhma, luoja)
VALUES
  ('Hietasaaren poijujen korjaus', (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'), false, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO vv_hinnoittelu
(nimi, "urakka-id", hintaryhma, luoja)
VALUES
  ('Muun väylän korjaus hintaryhmä', (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'), true, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO vv_hinnoittelu
(nimi, "urakka-id", hintaryhma, luoja)
VALUES
  ('Muun väylän korjaus', (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'), false, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, maara, luoja)
VALUES
  ((SELECT id FROM vv_hinnoittelu WHERE nimi = 'Hietasaaren poijujen korjaus hintaryhmä'),
   'Tilaus', 300, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, maara, luoja)
VALUES
  ((SELECT id FROM vv_hinnoittelu WHERE nimi = 'Hietasaaren poijujen korjaus'),
   'Tilaus', 60000, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, maara, luoja)
VALUES
  ((SELECT id FROM vv_hinnoittelu WHERE nimi = 'Muun väylän korjaus hintaryhmä'),
   'Tilaus', 100, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, maara, luoja)
VALUES
  ((SELECT id FROM vv_hinnoittelu WHERE nimi = 'Muun väylän korjaus'),
   'Tilaus', 200, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id")
VALUES
  ((SELECT id FROM reimari_toimenpide WHERE lisatieto = 'Poijujen korjausta kuten on sovittu'),
   (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Hietasaaren poijujen korjaus'));

INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id")
VALUES
  ((SELECT id FROM reimari_toimenpide WHERE lisatieto = 'Poijujen korjausta kuten on sovittu'),
   (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Hietasaaren poijujen korjaus hintaryhmä'));

INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id")
VALUES
  ((SELECT id FROM reimari_toimenpide WHERE lisatieto = 'Poijujen korjausta taas'),
   (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Hietasaaren poijujen korjaus hintaryhmä'));

INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id")
VALUES
  ((SELECT id FROM reimari_toimenpide WHERE lisatieto = 'Poijujen korjausta jossain ihan muualla'),
   (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Hietasaaren poijujen korjaus hintaryhmä'));

INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id")
VALUES
  ((SELECT id FROM reimari_toimenpide WHERE lisatieto = 'Yksittäisen poljun korjaus'),
   (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Muun väylän korjaus hintaryhmä'));

INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id")
VALUES
  ((SELECT id FROM reimari_toimenpide WHERE lisatieto = 'Yksittäisen poljun korjaus'),
   (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Muun väylän korjaus'));
-- ***********************************************
=======
-- Hintaryhmä

INSERT INTO vv_hinnoittelu
(nimi, hintaryhma, luoja, "urakka-id")
VALUES
  ('Hietasaaren poijujen korjaus', true,
   (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   (SELECT "urakka-id" FROM reimari_toimenpide WHERE lisatieto = 'Poijujen korjausta kuten on sovittu'));

INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, maara, luoja)
VALUES
  ((SELECT id FROM vv_hinnoittelu WHERE nimi = 'Hietasaaren poijujen korjaus'),
   'Muu', 60000, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id", luoja)
VALUES
  ((SELECT id FROM reimari_toimenpide WHERE lisatieto = 'Poijujen korjausta kuten on sovittu'),
   (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Hietasaaren poijujen korjaus'),
   (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

-- Toimenpiteen hintatiedot

INSERT INTO vv_hinnoittelu
(nimi, hintaryhma, luoja, "urakka-id")
VALUES
  ('Tämän ei pitäisi näkyä' , false,
   (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   (SELECT "urakka-id" FROM reimari_toimenpide WHERE lisatieto = 'Poijujen korjausta kuten on sovittu'));

INSERT INTO vv_hinnoittelu
(nimi, hintaryhma, luoja, "urakka-id", poistettu)
VALUES
  ('POISTETTU HINNOITTELU EI SAISI NÄKYÄ MISSÄÄN' , false,
   (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   (SELECT "urakka-id" FROM reimari_toimenpide WHERE lisatieto = 'TESTITOIMENPIDE 2'), true);


INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, maara, luoja)
VALUES
  ((SELECT id FROM vv_hinnoittelu WHERE nimi = 'Tämän ei pitäisi näkyä'),
   'Työ', 600, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, maara, luoja, poistettu)
VALUES
  ((SELECT id FROM vv_hinnoittelu WHERE nimi = 'Tämän ei pitäisi näkyä'),
   'POISTETTU HINTA EI SAA NÄKYÄ', 99999999, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'), true);
>>>>>>> HAR-5020_vesivayla_ykshint

INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, maara, luoja, poistettu)
VALUES
  ((SELECT id FROM vv_hinnoittelu WHERE nimi = 'POISTETTU HINNOITTELU EI SAISI NÄKYÄ MISSÄÄN'),
   'POISTETTUUN HINNOITTELUUN KUULUVA HINTA JOKA EI OLE POISTETTU EI SAA NÄKYÄ MISSÄÄN', 99999999, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'), false);


INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id", luoja)
VALUES
  ((SELECT id FROM reimari_toimenpide WHERE lisatieto = 'Poijujen korjausta kuten on sovittu'),
   (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Tämän ei pitäisi näkyä'),
   (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id", luoja)
VALUES
  ((SELECT id FROM reimari_toimenpide WHERE lisatieto = 'TESTITOIMENPIDE 2'),
   (SELECT id FROM vv_hinnoittelu WHERE nimi = 'POISTETTU HINNOITTELU EI SAISI NÄKYÄ MISSÄÄN'),
   (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

-- ***********************************************
-- TODO: ERIKSEEN TILATTU YKSIKKÖHINTAINEN TYÖ?
-- ***********************************************