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
