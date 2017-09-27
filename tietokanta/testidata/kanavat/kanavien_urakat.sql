INSERT INTO urakka (nimi, indeksi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, tyyppi,  harjassa_luotu, luotu, luoja, urakkanro, sampoid)
VALUES
  ('Saimaan kanava',
    'MAKU 2005 kunnossapidon osaindeksi',
    '2016-08-01', '2019-07-30',
    (SELECT id FROM organisaatio WHERE nimi = 'Kanavat ja avattavat sillat'),
    (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat'),
    (SELECT id FROM hanke WHERE nimi = 'Saimaan korjaushanke'),
    'vesivayla-kanavien-hoito',
    true, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'),
   '089123', 'kanava-HAR-123');