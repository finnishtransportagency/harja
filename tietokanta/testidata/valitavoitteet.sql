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
       true);

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