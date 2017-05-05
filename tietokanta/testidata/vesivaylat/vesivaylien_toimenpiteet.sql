-- VIKA ILMAN KORJAUSTA

INSERT INTO vv_vikailmoitus
("reimari-id", kuvaus, pvm, "turvalaite-id")
VALUES
  ('1234', 'Akonniemen kyltti on lähtenyt irti myrskyn takia', '2017-04-02', (SELECT id FROM vv_turvalaite WHERE nimi = 'Akonniemen kyltti'));

-- KOKONAISHINTAINEN TOIMENPIDE ILMAN VIKAA

INSERT INTO toteuma
(urakka, sopimus, luotu,luoja,alkanut, paattynyt, suorittajan_nimi, tyyppi, lahde, lisatieto)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
   (SELECT id
    FROM sopimus
    WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
   NOW(),
   (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-05-05 00:00:00+02',
   '2017-05-05 12:00:00+02',
   'Pohjanmeren venepojat',
   'vv-kokonaishintainen',
   'harja-api',
   'TESTITOIMENPIDE 1');

INSERT INTO reimari_toimenpide
("toteuma-id",
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
 "reimari-tyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
"vayla-id")
VALUES
  ((SELECT id
     FROM toteuma
     WHERE lisatieto = 'TESTITOIMENPIDE 1'),
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

-- KOKONAISHINTAINEN TOIMENPIDE VIALLA

INSERT INTO toteuma
(urakka, sopimus, luotu,
 luoja,alkanut, paattynyt, suorittajan_nimi, tyyppi, lahde, lisatieto)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
   (SELECT id
    FROM sopimus
    WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
   NOW(),
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-04-04 00:00:00+02',
   '2017-04-04 12:00:00+02',
   'Pohjanmeren venepojat',
   'vv-kokonaishintainen',
   'harja-api',
   'TESTITOIMENPIDE 2');

INSERT INTO reimari_toimenpide
("toteuma-id",
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
 "reimari-tyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vayla-id")
VALUES
  ((SELECT id
     FROM toteuma
     WHERE lisatieto = 'TESTITOIMENPIDE 2'),
    22,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus)',
    (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
    '(62, Hietasaaren viitta, 555)',
    (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren viitta'),
    '',
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
("reimari-id", kuvaus, pvm, "turvalaite-id", "toteuma-id")
    VALUES
      ('123', 'Hietasaaren viitta on kaatunut', '2017-04-02', (SELECT id FROM vv_turvalaite WHERE nimi = 'Hietasaaren viitta'),
        (SELECT id
         FROM toteuma
         WHERE lisatieto = 'TESTITOIMENPIDE 2'));

-- KOKONAISHINTAISIIN SIIRRETTY, REIMARISTA YKSIKKÖHINTAISENA RAPORTOITU TYÖ

INSERT INTO toteuma
(urakka, sopimus, luotu,
 luoja,alkanut, paattynyt, suorittajan_nimi, tyyppi, lahde, lisatieto)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
   (SELECT id
    FROM sopimus
    WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
   NOW(),
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-05-03 09:00:00+02',
   '2017-05-03 15:00:00+02',
   'Pohjanmeren venepojat',
   'vv-kokonaishintainen',
   'harja-api',
   'TESTITOIMENPIDE 3');

INSERT INTO reimari_toimenpide
("toteuma-id",
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
 "reimari-tyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vayla-id")
VALUES
  ((SELECT id
     FROM toteuma
     WHERE lisatieto = 'TESTITOIMENPIDE 3'),
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

-- YKSIKKÖHINTAISIIN SIIRRETTY TYÖ, ILMAN HINTAERITTELYÄ

INSERT INTO toteuma
(urakka, sopimus, luotu,
 luoja,alkanut, paattynyt, suorittajan_nimi, tyyppi, lahde, lisatieto)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
   (SELECT id
    FROM sopimus
    WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
   NOW(),
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-05-03 09:00:00+02',
   '2017-05-03 15:00:00+02',
   'Pohjanmeren venepojat',
   'vv-yksikkohintainen',
   'harja-api',
   'TESTITOIMENPIDE 4');

INSERT INTO reimari_toimenpide
("toteuma-id",
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
 "reimari-tyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vayla-id")
VALUES
  ((SELECT id
     FROM toteuma
     WHERE lisatieto = 'TESTITOIMENPIDE 4'),
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

-- YKSIKKÖHINTAISIIN SIIRRETTY TYÖ, HINTAERITTELY TEHTY

INSERT INTO toteuma
(urakka, sopimus, luotu,
 luoja,alkanut, paattynyt, suorittajan_nimi, tyyppi, lahde, lisatieto)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
   (SELECT id
    FROM sopimus
    WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
   NOW(),
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-05-03 09:00:00+02',
   '2017-05-03 15:00:00+02',
   'Pohjanmeren venepojat',
   'vv-yksikkohintainen',
   'harja-api',
   'TESTITOIMENPIDE 5');

INSERT INTO reimari_toimenpide
("toteuma-id",
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
 "reimari-tyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vayla-id")
VALUES
  ((SELECT id
     FROM toteuma
     WHERE lisatieto = 'TESTITOIMENPIDE 5'),
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
   '1022542001',
   '1022541802',
   '1022541905',
   '(123, Hietasaaren läntinen rinnakkaisväylä, 55)',
   (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

-- REIMARISTA YKSIKKÖHINTAISENA RAPORTOITU TYÖ

INSERT INTO toteuma
(urakka, sopimus, luotu,
 luoja,alkanut, paattynyt, suorittajan_nimi, tyyppi, lahde, lisatieto)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
   (SELECT id
    FROM sopimus
    WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
   NOW(),
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-05-08 08:00:00+02',
   '2017-05-08 18:00:00+02',
   'Pohjanmeren venepojat',
   'vv-yksikkohintainen',
   'harja-api',
   'TESTITOIMENPIDE 6');

INSERT INTO reimari_toimenpide
("toteuma-id",
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
 "reimari-tyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vayla-id")
VALUES
  ((SELECT id
     FROM toteuma
     WHERE lisatieto = 'TESTITOIMENPIDE 6'),
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
    TRUE,
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

INSERT INTO vv_hinta
("toteuma-id", hinta, luoja, luotu, kuvaus, tyyppi)
    VALUES
      ((SELECT id
        FROM toteuma
        WHERE lisatieto = 'TESTITOIMENPIDE 6'), 500, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
       '2017-05-08T23:23Z', 'Matka poijulle', 'matka');

INSERT INTO vv_hinta
("toteuma-id", hinta, luoja, luotu, kuvaus, tyyppi)
VALUES
  ((SELECT id
    FROM toteuma
    WHERE lisatieto = 'TESTITOIMENPIDE 6'), 9000, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-05-08T23:23Z', 'Aurinkopaneeli', 'komponentti');

INSERT INTO vv_hinta
("toteuma-id", hinta, luoja, luotu, kuvaus, tyyppi)
VALUES
  ((SELECT id
    FROM toteuma
    WHERE lisatieto = 'TESTITOIMENPIDE 6'), 800, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-05-08T23:23Z', 'Asennustyö', 'tyo');

INSERT INTO vv_hinta
("toteuma-id", hinta, luoja, luotu, kuvaus, tyyppi, yleiskustannuslisa)
VALUES
  ((SELECT id
    FROM toteuma
    WHERE lisatieto = 'TESTITOIMENPIDE 6'), 300, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-05-08T23:23Z', 'puutavara', 'materiaali', TRUE);

-- REIMARISTA YKSIKKÖHINTAISENA RAPORTOITU, KÖNTTÄSUMMALLA HINNOITELTU TOIMENPIDE

INSERT INTO toteuma
(urakka, sopimus, luotu,
 luoja,alkanut, paattynyt, suorittajan_nimi, tyyppi, lahde, lisatieto)
VALUES
  ((SELECT id
    FROM urakka
    WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
   (SELECT id
    FROM sopimus
    WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
   NOW(),
   (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-05-08 08:00:00+02',
   '2017-05-08 12:50:00+02',
   'Pohjanmeren venepojat',
   'vv-yksikkohintainen',
   'harja-api',
   'TESTITOIMENPIDE 7');

INSERT INTO reimari_toimenpide
("toteuma-id",
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
 "reimari-tyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vayla-id")
VALUES
  ((SELECT id
    FROM toteuma
    WHERE lisatieto = 'TESTITOIMENPIDE 7'),
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
    TRUE,
    '2017-05-08T13:23Z',
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

INSERT INTO vv_hinta
("toteuma-id", hinta, luoja, luotu, kuvaus, tyyppi)
VALUES
  ((SELECT id
    FROM toteuma
    WHERE lisatieto = 'TESTITOIMENPIDE 7'), 15000, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '2017-05-08T13:23Z', 'Kokonaishinta', 'muu');

-- TODO: ERIKSEEN TILATTU YKSIKKÖHINTAINEN TYÖ