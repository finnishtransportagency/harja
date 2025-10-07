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

---------------------------------------------
-- OULUN MHU
---------------------------------------------

-- Urakkakohtaiset

-- HOITOVUOSI 2019-2020 (1. hoitovuosi)
INSERT INTO valitavoite (urakka, nimi, takaraja, valmis_pvm, valmis_kommentti, poistettu)
VALUES 
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV1: Talvikauden valmistelut tehty',
   '2019-10-15',
   '2019-10-10',
   'Kalusto ja materiaalit valmiina',
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV1: Ensimmäinen lumenauraus suoritettu',
   '2019-11-30',
   '2019-11-28',
   'Auraus onnistui hyvin',
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV1: Liukkauden torjunta käynnissä',
   '2019-12-31',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV1: Kevään kunnostustyöt aloitettu',
   '2020-04-30',
   '2020-04-25',
   'Kunnostus aloitettu aikataulussa',
   false);

-- HOITOVUOSI 2020-2021 (2. hoitovuosi)
INSERT INTO valitavoite (urakka, nimi, takaraja, valmis_pvm, valmis_kommentti, poistettu)
VALUES 
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV2: Kesähuoltokauden aloitus',
   '2020-05-15',
   '2020-05-14',
   'Kesähuolto aloitettu',
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV2: Päällysteiden paikkaus kesällä',
   '2020-08-31',
   '2020-08-28',
   'Paikkaukset tehty suunnitelman mukaan',
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV2: Syksyn varautuminen talveen',
   '2020-09-30',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV2: Talvikaluston testaus',
   '2020-10-31',
   '2020-10-29',
   'Kalusto testattu ja toimintakuntoinen',
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV2: Joulukuun liukkauden torjunta',
   '2020-12-31',
   '2020-12-30',
   'Liukkaus torjuttu tehokkaasti',
   false);

-- HOITOVUOSI 2021-2022 (3. hoitovuosi)
INSERT INTO valitavoite (urakka, nimi, takaraja, valmis_pvm, valmis_kommentti, poistettu)
VALUES 
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV3: Talven aurauskalusto käytössä',
   '2021-11-01',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV3: Joulun sääolosuhteet hallinnassa',
   '2021-12-24',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV3: Talven puolivälin arviointi',
   '2022-01-31',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV3: Kevään sulatusvesien hallinta',
   '2022-04-15',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV3: Kesän tienhoitotyöt',
   '2022-08-31',
   NULL,
   NULL,
   false);

-- HOITOVUOSI 2022-2023 (4. hoitovuosi)
INSERT INTO valitavoite (urakka, nimi, takaraja, valmis_pvm, valmis_kommentti, poistettu)
VALUES 
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV4: Syksyn teiden kunnostus',
   '2022-10-31',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV4: Talven ensimmäinen suolakierros',
   '2022-11-15',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV4: Joulun liikenneturvallisuus',
   '2022-12-23',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV4: Talven loppuvaiheen toimet',
   '2023-03-31',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV4: Kevätkunnon saavuttaminen',
   '2023-05-31',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV4: Kesän tiemerkinnät',
   '2023-07-15',
   NULL,
   NULL,
   false);

-- HOITOVUOSI 2023-2024 (5. hoitovuosi, viimeinen)
INSERT INTO valitavoite (urakka, nimi, takaraja, valmis_pvm, valmis_kommentti, poistettu)
VALUES 
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV5: Viimeisen hoitovuoden aloitus',
   '2023-10-01',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV5: Loppuvuoden talvihoito',
   '2023-12-31',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV5: Kevään kuntoarvio tehty',
   '2024-05-31',
   '2024-05-20',
   'Merkinnät kunnostettu hyvällä laadulla',
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV5: Sorateiden hoito',
   '2024-08-15',
   NULL,
   NULL,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV5: Vesakonraivaus',
   '2024-07-01',
   '2024-06-25',
   'Raivaus suoritettu tehokkaasti',
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'HV5: Urakan päättävät toimenpiteet',
   '2024-09-30',
   NULL,
   NULL,
   false);

-- Valtakunnalliset välitavoitteet eri hoitovuosille
INSERT INTO valitavoite (urakka, nimi, takaraja, valtakunnallinen_valitavoite, poistettu)
VALUES 
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'VK HV1: Koko Suomen liikenneympäristö hoidettu',
   '2020-01-01',
   21,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'VK HV2: Koko Suomen liikenneympäristö hoidettu',
   '2021-01-01',
   21,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'VK HV3: Koko Suomen liikenneympäristö hoidettu',
   '2022-01-01',
   21,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'VK HV4: Kevään kuntoarvo raportoitu',
   '2023-05-31',
   6,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'VK HV5: Kevään kuntoarvo raportoitu',
   '2024-05-31',
   6,
   false),
  
  ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
   'VK HV5: Syksyn kuntoarvo raportoitu',
   '2024-10-15',
   7,
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

-- ================================================================
-- PÄÄLLYSTYSURAKOIDEN VÄLITAVOITTEET
-- ================================================================

-- Utajärven päällystysurakka (id: 7, 2021-2025)
-- Urakan omat välitavoitteet (ei kohdekohtaisia)
INSERT INTO valitavoite (nimi, tyyppi, takaraja, valmis_pvm, urakka, yllapitokohde, poistettu, luotu, luoja, muokattu, muokkaaja)
VALUES 
    ('Päällystyskohdeluettelo hyväksytty', 'kertaluontoinen', '2021-04-30', NULL, 7, NULL, FALSE, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'), NULL, NULL),
    ('Kaikki kohteet aloitettu', 'kertaluontoinen', '2021-06-30', '2021-06-15', 7, NULL, FALSE, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'), NULL, NULL),
    ('Vuoden 2021 kohteet valmis', 'kertaluontoinen', '2021-09-30', '2021-09-28', 7, NULL, FALSE, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'), NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus')),
    ('Talvikauden tarkastus', 'kertaluontoinen', '2022-03-31', '2022-03-29', 7, NULL, FALSE, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'), NULL, NULL),
    ('Kesäkauden kohteet käynnissä', 'kertaluontoinen', '2022-07-15', NULL, 7, NULL, FALSE, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'), NULL, NULL);
