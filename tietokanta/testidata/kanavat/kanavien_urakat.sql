INSERT INTO hanke (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Saimaan huolto- ja kunnossapito', '2016-07-07', '2021-05-05', true, NOW());

INSERT INTO urakka (nimi, indeksi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, tyyppi,  harjassa_luotu, luotu, luoja, urakkanro, sampoid)
VALUES
  ('Saimaan kanava',
    'MAKU 2005 kunnossapidon osaindeksi',
    '2016-08-01', '2019-07-30',
    (SELECT id FROM organisaatio WHERE nimi = 'Kanavat ja avattavat sillat'),
    (SELECT id FROM organisaatio WHERE nimi = 'Saimaan huolto'),
    (SELECT id FROM hanke WHERE nimi = 'Saimaan huolto- ja kunnossapito'),
    'vesivayla-kanavien-hoito',
    true, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '089123', 'kanava-HAR-123');

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Saimaan huollon pääsopimus',
        (SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'),
        NULL,
        '2016-08-01', '2018-07-30', true, NOW());

INSERT INTO hanke (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Joensuun huolto- ja kunnossapito', '2016-07-07', '2021-05-05', true, NOW());

INSERT INTO urakka (nimi, indeksi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, tyyppi,  harjassa_luotu, luotu, luoja, urakkanro, sampoid)
VALUES
  ('Joensuun kanava',
    'MAKU 2005 kunnossapidon osaindeksi',
    '2016-08-01', '2019-07-30',
    (SELECT id FROM organisaatio WHERE nimi = 'Kanavat ja avattavat sillat'),
    (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'),
    (SELECT id FROM hanke WHERE nimi = 'Joensuun huolto- ja kunnossapito'),
    'vesivayla-kanavien-hoito',
    true, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '089123', 'kanava-HAR-124');

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Joensuun huollon pääsopimus',
        (SELECT id FROM urakka WHERE nimi = 'Joensuun kanava'),
        NULL,
        '2016-08-01', '2018-07-30', true, NOW());