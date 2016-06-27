INSERT INTO valitavoite (urakka, nimi, takaraja, valmis_pvm, valmis_kommentti, poistettu)
VALUES ((SELECT id
                   FROM   urakka
                   WHERE  nimi = 'Oulun alueurakka 2014-2019'),
                   'Koko Suomi aurattu',
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
                   false)