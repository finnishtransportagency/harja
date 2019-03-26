-- URAKOITSIJA
INSERT INTO organisaatio (nimi, ytunnus, tyyppi, harjassa_luotu, luotu)
VALUES ('Saimaan huolto', '1729662-9', 'urakoitsija', true, '2017-10-01'::DATE);

INSERT INTO organisaatio (nimi, ytunnus, katuosoite, postinumero, postitoimipaikka, tyyppi, harjassa_luotu, luotu)
VALUES ('Pohjanmeren venepojat', '0472549-4', 'Oulaistenkatu 28', 8600, 'Oulainen', 'urakoitsija', true, '2017-10-01'::DATE);

-- HANKE
INSERT INTO hanke (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Saimaan kartoitus', '2014-07-07', '2015-05-05', true, '2017-10-01'::DATE);

INSERT INTO hanke (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Saimaan korjaushanke', '2016-07-07', '2021-05-05', true, '2017-10-01'::DATE);

INSERT INTO hanke (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Pohjanmeren hoitohanke', '2021-07-07', '2030-05-05', true, '2017-10-01'::DATE);

INSERT INTO hanke (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Pyhäselän syvennyshanke', '2016-08-01', '2019-07-30', true, '2017-10-01'::DATE);


INSERT INTO hanke (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Rentoselän syvennyshanke', '2016-08-01', '2019-07-30', true, '2017-10-01'::DATE);

-- URAKKA
INSERT INTO urakka (nimi, indeksi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, tyyppi,  harjassa_luotu, luotu, luoja, urakkanro, sampoid)
VALUES
  ('Vantaan väyläyksikön väylänhoito ja -käyttö, Itäinen SL',
    'MAKU 2005 kunnossapidon osaindeksi',
    '2013-08-01', '2016-07-30',
    (SELECT id FROM organisaatio WHERE nimi = 'Meriväylät'),
    (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'),
    (SELECT id FROM hanke WHERE nimi = 'Saimaan kartoitus'),
    'vesivayla-hoito',
    true, '2017-10-01'::DATE, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
    444, 'vv-HAR-123');


INSERT INTO urakka (nimi, indeksi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, tyyppi,  harjassa_luotu, luotu, luoja, urakkanro, sampoid)
VALUES
  ('Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL',
    'MAKU 2005 kunnossapidon osaindeksi',
    '2016-08-01', '2019-07-30',
    (SELECT id FROM organisaatio WHERE nimi = 'Meriväylät'),
    (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'),
    (SELECT id FROM hanke WHERE nimi = 'Saimaan korjaushanke'),
    'vesivayla-hoito',
    true, '2017-10-01'::DATE, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
    '3332,555,3331', 'vv-HAR-124');
-- Huom. Tiedoston lopussa päivitetään vesiväyläurakoiden urakkanumerot ja luodaan urakka-alueet HAR-8439.


INSERT INTO urakka (nimi, indeksi, alkupvm, loppupvm, tyyppi,  harjassa_luotu, luotu, luoja, sampoid)
VALUES
  ('Kotkan väyläyksikön väylänhoito ja -käyttö, Itäinen SL',
    'MAKU 2005 kunnossapidon osaindeksi',
   '2016-08-01', '2019-07-30',
   'vesivayla-hoito',
   true, '2017-10-01'::DATE, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'), 'vv-HAR-125');

INSERT INTO urakka (nimi, indeksi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, tyyppi,  harjassa_luotu, luotu, luoja, sampoid)
VALUES
  ('Turun väyläyksikön väylänhoito ja -käyttö, Itäinen SL',
    'MAKU 2010 Maarakennuskustannukset, kokonaisindeksi',
   '2019-08-01', '2024-07-30',
   (SELECT id FROM organisaatio WHERE nimi = 'Meriväylät'),
   (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'),
   (SELECT id FROM hanke WHERE nimi = 'Pohjanmeren hoitohanke'),
   'vesivayla-hoito',
   true, '2017-10-01'::DATE, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'), 'vv-HAR-126');

INSERT INTO urakka (nimi, indeksi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, tyyppi,  harjassa_luotu, luotu, luoja, urakkanro, sampoid)
VALUES
  ('Pyhäselän urakka',
    'MAKU 2010 Maarakennuskustannukset, kokonaisindeksi',
    '2016-08-01', '2019-07-30',
    (SELECT id FROM organisaatio WHERE nimi = 'Sisävesiväylät'),
    (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'),
    (SELECT id FROM hanke WHERE nimi = 'Pyhäselän syvennyshanke'),
    'vesivayla-hoito',
    true, '2017-10-01'::DATE, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '555', 'vv-HAR-1249');

INSERT INTO urakka (nimi, indeksi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, tyyppi,  harjassa_luotu, luotu, luoja, urakkanro, sampoid)
VALUES
  ('Rentoselän urakka',
    'MAKU 2010 Maarakennuskustannukset, kokonaisindeksi',
    '2016-08-01', '2019-07-30',
    (SELECT id FROM organisaatio WHERE nimi = 'Sisävesiväylät'),
    (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'),
    (SELECT id FROM hanke WHERE nimi = 'Rentoselän syvennyshanke'),
    'vesivayla-hoito',
    true, '2017-10-01'::DATE, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '555', 'vv-HAR-1250');

-- SOPIMUS
INSERT INTO sopimus (nimi, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Kotkan väyläyksikön pääsopimus',  NULL,
        '2013-08-01', '2016-07-30', true, '2017-10-01'::DATE);

INSERT INTO sopimus (nimi, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Vapaa sopimus',  NULL,
        '2013-08-01', '2016-07-30', true, '2017-10-01'::DATE);

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Helsingin väyläyksikön pääsopimus',
        (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        NULL,
        '2016-08-01', '2018-07-30', true, '2017-10-01'::DATE);

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Turun väyläyksikön pääsopimus',
        (SELECT id FROM urakka WHERE nimi = 'Turun väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        NULL,
        '2019-08-01', '2024-07-30', true, '2017-10-01'::DATE);

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Helsingin väyläyksikön sivusopimus',
        (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'),
        '2016-08-01', '2018-07-30', true, '2017-10-01'::DATE);

INSERT INTO sopimus (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Meriväylien sopimus','2016-08-01', '2018-07-30', true, '2017-10-01'::DATE);

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Vantaan väyläyksikön pääsopimus',
        (SELECT id FROM urakka WHERE nimi = 'Vantaan väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        NULL,
        '2021-08-01', '2024-07-30',
        true, '2017-10-01'::DATE);


INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Pyhäselän pääsopimus',
        (SELECT id FROM urakka WHERE nimi = 'Pyhäselän urakka'),
        NULL,
        '2016-08-01', '2019-07-30', true, '2017-10-01'::DATE);

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Rentoselän pääsopimus',
        (SELECT id FROM urakka WHERE nimi = 'Rentoselän urakka'),
        NULL,
        '2016-08-01', '2019-07-30', true, '2017-10-01'::DATE);

-- reimarin sopimus-id linkkaukset
INSERT INTO reimari_sopimuslinkki ("harja-sopimus-id", "reimari-sopimus-id") VALUES ((SELECT id FROM sopimus WHERE nimi = 'Meriväylien sopimus'), -666);
INSERT INTO reimari_sopimuslinkki ("harja-sopimus-id", "reimari-sopimus-id") VALUES ((SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'), -5);
INSERT INTO reimari_sopimuslinkki ("harja-sopimus-id", "reimari-diaarinro") VALUES ((SELECT id FROM sopimus WHERE nimi = 'Vantaan väyläyksikön pääsopimus'), '123/45');
INSERT INTO reimari_sopimuslinkki ("harja-sopimus-id", "reimari-sopimus-id") VALUES ((SELECT id FROM sopimus WHERE nimi = 'Pyhäselän pääsopimus'), -25);
INSERT INTO reimari_sopimuslinkki ("harja-sopimus-id", "reimari-sopimus-id") VALUES ((SELECT id FROM sopimus WHERE nimi = 'Rentoselän pääsopimus'), -26);

-- TOIMENPIDEKOODIT
INSERT INTO toimenpidekoodi (taso, emo, nimi)
VALUES (3, 132, 'Rannikon kauppamerenkulku');
INSERT INTO toimenpidekoodi (taso, emo, nimi)
VALUES (3, 132, 'Rannikon muut');

-- URAKKA-ALUEET
-- Päivitä olemassaolevien vesiväyläurakoiden urakka-aluetieto vastaamaan uutta toteutusta HAR-8439
INSERT INTO vv_urakka_turvalaiteryhma (urakka, turvalaiteryhmat, alkupvm, loppupvm, luotu, luoja)
  (SELECT id, string_to_array(urakkanro, ','), alkupvm, loppupvm, NOW(),
          (select id from kayttaja where kayttajanimi = 'Integraatio') from urakka where tyyppi = 'vesivayla-hoito' and urakkanro is not null);
UPDATE urakka
set urakkanro = (select v.id from vv_urakka_turvalaiteryhma v where v.urakka = urakka.id)::TEXT,
    muokattu = NOW(),
    muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE tyyppi = 'vesivayla-hoito' and urakkanro is not null;
