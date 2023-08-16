---------------------------------------------
-- OULUN ALUEURAKKA
---------------------------------------------

-- Urakkakohtaiset

INSERT INTO valitavoite (urakka, nimi, takaraja, valmis_pvm, valmis_kommentti, poistettu)
VALUES ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Oulun alueurakka 2014-2019'),
         'Koko urakan alue aurattu',
         '2014-05-29',
         '2014-05-01',
         'Homma hoidettu hyvästi ennen tavoitepäivää!',
         false),
         ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Oulun alueurakka 2014-2019'),
         'Pelkosentie 678 suolattu',
         '2015-09-23',
         '2015-09-25',
         'Aurattu, mutta vähän tuli myöhässä',
         false),
         ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Oulun alueurakka 2014-2019'),
         'Sepon mökkitie suolattu',
         '2014-12-24',
         NULL,
         NULL,
         false),
         ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Oulun alueurakka 2014-2019'),
         'Oulaisten liikenneympyrä aurattu',
         '2050-1-1',
         NULL,
         NULL,
         false);

-- Valtakunnalliset (kertaluontoiset)

INSERT INTO valitavoite (urakka, nimi, urakkatyyppi, takaraja, tyyppi, poistettu)
VALUES (null,
       'Koko Suomi aurattu',
       'hoito',
       '2019-05-29',
       'kertaluontoinen'::valitavoite_tyyppi,
       false),
       (null,
       'Koko Suomi tiemerkitty',
       'tiemerkinta',
       '2019-05-29',
       'kertaluontoinen'::valitavoite_tyyppi,
       false),
       (null,
       'Liikennemerkit tarkistettu',
       'hoito',
       '2015-05-29',
       'kertaluontoinen'::valitavoite_tyyppi,
       true),
       (null,
       'Kaikkien urakoiden kalusto huollettu',
       'hoito',
       null,
       'kertaluontoinen'::valitavoite_tyyppi,
       false),
       (null,
       'Koko Suomi suolattu',
       'hoito',
       '2005-8-23',
       'kertaluontoinen'::valitavoite_tyyppi,
       false);

-- Valtakunnalliset (toistuvat)

INSERT INTO valitavoite (urakka, nimi, urakkatyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, tyyppi, poistettu)
VALUES (null,
       'Koko Suomen liikenneympäristö hoidettu',
       'hoito',
       1,
       1,
       'toistuva'::valitavoite_tyyppi,
       false),
       (null,
       'Koko Suomen tiemerkintä suoritettu',
       'tiemerkinta',
       6,
       6,
       'toistuva'::valitavoite_tyyppi,
       false),
       (null,
       'Kaikki tiet putsattu',
       'hoito',
       1,
       1,
       'toistuva'::valitavoite_tyyppi,
       true),
       (null,
        'Pidä urakoitsijan kanssa kunnon syyskekkerit',
        'teiden-hoito',
        30,
        9,
        'toistuva'::valitavoite_tyyppi,
        false);

---------------------------------------------
-- Muhoksen päällystysurakka
---------------------------------------------

INSERT INTO valitavoite (urakka, yllapitokohde, nimi, takaraja, valmis_pvm, valmis_kommentti, poistettu)
VALUES ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Muhoksen päällystysurakka'),
        (SELECT id FROM yllapitokohde WHERE nimi = 'Leppäjärven ramppi'),
        'Se iso kivi siirretty pois tieltä',
        '2017-05-29',
        '2017-05-29',
        'Homma hoidettu hyvästi ennen tavoitepäivää!',
        false),
  ((SELECT id
    FROM   urakka
    WHERE  nimi = 'Muhoksen päällystysurakka'),
   (SELECT id FROM yllapitokohde WHERE nimi = 'Leppäjärven ramppi'),
   'RP-työt tehty',
   '2017-05-30',
   NULL,
   'Hyvää laatua toivottiin',
   false),
  ((SELECT id
    FROM   urakka
    WHERE  nimi = 'Muhoksen päällystysurakka'),
   (SELECT id FROM yllapitokohde WHERE nimi = 'Oulaisten ohitusramppi'),
   'Koko homma paketissa',
   '2017-06-05',
   NULL,
   NULL,
   false),
  ((SELECT id
    FROM   urakka
    WHERE  nimi = 'Muhoksen päällystysurakka'),
   NULL,
   'Koko homma valamis',
   '2017-06-08',
   NULL,
   NULL,
   false);

INSERT INTO valitavoite (urakka, nimi, takaraja, viikkosakko, sakko, valmis_pvm, valmis_kommentti, valmis_merkitsija, valmis_merkitty, luotu, muokattu, luoja, muokkaaja, poistettu, tyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, valtakunnallinen_valitavoite, urakkatyyppi, aloituspvm, yllapitokohde)
VALUES
       ((SELECT id FROM urakka where nimi = 'Raahen MHU 2023-2028'), 'Pidä urakoitsijan kanssa kunnon syyskekkerit', '2023-09-30', null, null, null, null, null, null, '2023-08-15 12:53:34.965786', null, 3, null, false, null, null, null, (select id from valitavoite where nimi = 'Pidä urakoitsijan kanssa kunnon syyskekkerit' AND valtakunnallinen_valitavoite IS NULL), null, null, null),
       ((SELECT id FROM urakka where nimi = 'Raahen MHU 2023-2028'), 'Pidä urakoitsijan kanssa kunnon syyskekkerit', '2024-09-30', null, null, null, null, null, null, '2023-08-15 12:53:34.965786', null, 3, null, false, null, null, null, (select id from valitavoite where nimi = 'Pidä urakoitsijan kanssa kunnon syyskekkerit' AND valtakunnallinen_valitavoite IS NULL), null, null, null),
       ((SELECT id FROM urakka where nimi = 'Raahen MHU 2023-2028'), 'Pidä urakoitsijan kanssa kunnon syyskekkerit', '2025-09-30', null, null, null, null, null, null, '2023-08-15 12:53:34.965786', null, 3, null, false, null, null, null, (select id from valitavoite where nimi = 'Pidä urakoitsijan kanssa kunnon syyskekkerit' AND valtakunnallinen_valitavoite IS NULL), null, null, null);
