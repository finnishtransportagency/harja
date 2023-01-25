INSERT INTO hanke (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Saimaan huolto- ja kunnossapito', '2016-07-07', '2021-05-05', TRUE, NOW());

INSERT INTO urakka (nimi, indeksi, alkupvm, loppupvm, hallintayksikko, urakoitsija,
                    hanke, tyyppi, harjassa_luotu, luotu, luoja, urakkanro, sampoid)
VALUES
   ('Saimaan kanava',
    'MAKU 2005 kunnossapidon osaindeksi',
    '2016-01-01', '2026-12-31',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Kanavat ja avattavat sillat'),
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Saimaan huolto'),
    (SELECT id
     FROM hanke
     WHERE nimi = 'Saimaan huolto- ja kunnossapito'),
    'vesivayla-kanavien-hoito',
    FALSE, NOW(), (SELECT id
                  FROM kayttaja
                  WHERE kayttajanimi = 'tero'),
   '089123', 'kanava-HAR-123');

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Saimaan huollon pääsopimus',
        (SELECT id
         FROM urakka
         WHERE nimi = 'Saimaan kanava'),
        NULL,
        '2016-01-01', '2026-12-31', TRUE, NOW());

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Saimaan huollon lisäsopimus',
        (SELECT id
         FROM urakka
         WHERE nimi = 'Saimaan kanava'),
        (SELECT id
         FROM sopimus
         WHERE nimi = 'Saimaan huollon pääsopimus'),
        '2016-01-01', '2026-12-31', TRUE, NOW());

INSERT INTO hanke (nimi, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Joensuun huolto- ja kunnossapito', '2016-07-07', '2021-05-05', TRUE, NOW());

INSERT INTO urakka (nimi, indeksi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, tyyppi, harjassa_luotu, luotu, luoja, urakkanro, sampoid)
VALUES
  ('Joensuun kanava',
    'MAKU 2005 kunnossapidon osaindeksi',
    '2022-01-01', '2026-12-31',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Kanavat ja avattavat sillat'),
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    (SELECT id
     FROM hanke
     WHERE nimi = 'Joensuun huolto- ja kunnossapito'),
    'vesivayla-kanavien-hoito',
    FALSE, NOW(), (SELECT id
                  FROM kayttaja
                  WHERE kayttajanimi = 'tero'),
   '089123', 'kanava-HAR-124');

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Joensuun huollon pääsopimus',
        (SELECT id
         FROM urakka
         WHERE nimi = 'Joensuun kanava'),
        NULL,
        '2022-01-01', '2026-12-31', TRUE, NOW());

INSERT INTO sopimus (nimi, urakka, paasopimus, alkupvm, loppupvm, harjassa_luotu, luotu)
VALUES ('Joensuun huollon lisäsopimus',
        (SELECT id
         FROM urakka
         WHERE nimi = 'Joensuun kanava'),
        (SELECT id
         FROM sopimus
         WHERE nimi = 'Joensuun huollon pääsopimus'),
        '2022-01-01', '2026-12-31', TRUE, NOW());
