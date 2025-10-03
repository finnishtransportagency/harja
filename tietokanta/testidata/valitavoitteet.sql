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

---------------------------------------------
-- MHU-URAKAT (TESTIDATA)
---------------------------------------------

-- Rovaniemen MHU testiurakka (1. hoitovuosi)

INSERT INTO valitavoite (urakka, nimi, takaraja, valmis_pvm, valmis_kommentti, poistettu)
VALUES ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)'),
         'TEST: Talvihoito valmis',
         '2026-02-28',
         '2026-02-15',
         'Talvihoito suoritettu suunnitellusti',
         false),
         ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)'),
         'TEST: Kesähuolto tehty',
         '2026-06-30',
         NULL,
         NULL,
         false),
         ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)'),
         'TEST: Syksyn hoito aloitettu',
         '2026-09-15',
         NULL,
         NULL,
         false);

-- Kemin MHU testiurakka (5. hoitovuosi)

INSERT INTO valitavoite (urakka, nimi, takaraja, valmis_pvm, valmis_kommentti, poistettu)
VALUES ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Kemin MHU testiurakka (5. hoitovuosi)'),
         'TEST: Vuosittainen kunnossapito',
         '2026-10-01',
         '2026-09-28',
         'Kunnossapito valmistui ennen määräaikaa',
         false),
         ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Kemin MHU testiurakka (5. hoitovuosi)'),
         'TEST: Liikennemerkkien tarkistus',
         '2026-03-15',
         NULL,
         NULL,
         false);

-- Oulun MHU 2019-2024

INSERT INTO valitavoite (urakka, nimi, takaraja, valmis_pvm, valmis_kommentti, poistettu)
VALUES ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Oulun MHU 2019-2024'),
         'TEST: Tiemerkinnät kunnostettu',
         '2024-05-31',
         '2024-05-20',
         'Merkinnät kunnostettu hyvällä laadulla',
         false),
         ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Oulun MHU 2019-2024'),
         'TEST: Sorateiden hoito',
         '2024-08-15',
         NULL,
         NULL,
         false),
         ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Oulun MHU 2019-2024'),
         'TEST: Vesakonraivaus',
         '2024-07-01',
         '2024-06-25',
         'Raivaus suoritettu tehokkaasti',
         false);

-- Kittilän MHU 2025-2030

INSERT INTO valitavoite (urakka, nimi, takaraja, valmis_pvm, valmis_kommentti, poistettu)
VALUES ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Kittilän MHU 2025-2030'),
         'TEST: Talvihiekoitus valmis',
         '2026-01-31',
         NULL,
         NULL,
         false),
         ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Kittilän MHU 2025-2030'),
         'TEST: Kesäteiden päällystehoito',
         '2026-07-15',
         '2026-07-10',
         'Päällystehoito onnistui erinomaisesti',
         false);

---------------------------------------------
-- VALTAKUNNALLISET VÄLITAVOITTEET URAKOISSA (TESTIDATA)
---------------------------------------------

-- Rovaniemen MHU testiurakka - valtakunnalliset tavoitteet

INSERT INTO valitavoite (urakka, nimi, takaraja, valtakunnallinen_valitavoite, poistettu)
VALUES ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)'),
         'TEST: Koko Suomen liikenneympäristö hoidettu',
         '2026-01-01',
         21,
         false),
         ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)'),
         'TEST: Koko Suomen tiemerkintä suoritettu',
         '2026-06-06',
         22,
         false);

-- Kemin MHU testiurakka - valtakunnalliset tavoitteet

INSERT INTO valitavoite (urakka, nimi, takaraja, valtakunnallinen_valitavoite, poistettu)
VALUES ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Kemin MHU testiurakka (5. hoitovuosi)'),
         'TEST: Massavaatimusteiden keskiviistaton merkinnät kunnostettu',
         '2026-07-31',
         1,
         false),
         ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Kemin MHU testiurakka (5. hoitovuosi)'),
         'TEST: Suojatiet ja pyörätiet kunnostettu',
         '2026-07-31',
         4,
         false);

-- Oulun MHU 2019-2024 - valtakunnalliset tavoitteet

INSERT INTO valitavoite (urakka, nimi, takaraja, valtakunnallinen_valitavoite, poistettu)
VALUES ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Oulun MHU 2019-2024'),
         'TEST: Kevään kuntoarvo, massavaatimustiet, pituussuuntaiset merkinnät raportoitu',
         '2024-05-31',
         6,
         false),
         ((SELECT id
         FROM   urakka
         WHERE  nimi = 'Oulun MHU 2019-2024'),
         'TEST: Syksyn kuntoarvo, maalivaatimustiet, pituussuuntaiset merkinnät raportoitu',
         '2024-10-15',
         7,
         false);