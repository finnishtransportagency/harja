INSERT INTO silta (tyyppi, siltanro, siltanimi, alue, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, siltatunnus, siltaid) VALUES (1,  1537, 'Oulujoen silta', ST_GeomFromText('LINESTRING (429718 7211747,   429721 7211866)')::GEOMETRY, 4,	401, 1945, 401, 2000, 'O-00001', 112345);
INSERT INTO silta (tyyppi, siltanro, siltanimi, alue, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, siltatunnus, siltaid) VALUES (1,   902, 'Oulujoen silta', ST_GeomFromText('MULTILINESTRING ((429718 7211777,   429721 7211896))')::GEOMETRY, 4,	401, 1945, 401, 2000, 'O-00002', 212345);
INSERT INTO silta (tyyppi, siltanro, siltanimi, alue, siltatunnus, siltaid) VALUES (2,   325235, 'Kempeleen testisilta',ST_GeomFromText('LINESTRING(436858.43500000006 7193422.615, 436208.1980000001 7192283.063)')::GEOMETRY, 'O-00003', 312345);
INSERT INTO silta (tyyppi,siltanro, siltanimi, alue, siltatunnus, siltaid) VALUES (2, 6666, 'Joutsensilta', ST_GeomFromText('LINESTRING (427714 7208942, 427844 7208968, 427948 7209015, 428154 7209152)')::GEOMETRY, 'O-00004', 412345);
INSERT INTO silta (tyyppi,siltanro, siltanimi, alue, siltatunnus, siltaid) VALUES (2, 7777, 'Kajaanintien silta', ST_GeomFromText('LINESTRING (429377 7210590, 429790 7210633)')::GEOMETRY, 'O-00005', 512345);

INSERT INTO siltatarkastus (lahde, tarkastusaika, tarkastaja, silta, urakka) VALUES ('harja-ui'::lahde, '2006-04-15', 'Sini Sillantarkastaja', (SELECT id from silta WHERE siltanro = 1537), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'));
INSERT INTO siltatarkastus (lahde, tarkastusaika, tarkastaja, silta, urakka) VALUES ('harja-ui'::lahde, '2007-02-25', 'Sirkka Sillankoestaja', (SELECT id from silta WHERE siltanro = 1537), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'));
INSERT INTO siltatarkastus (lahde, tarkastusaika, tarkastaja, silta, urakka) VALUES ('harja-ui'::lahde, '2007-05-05', 'Mari Mittatarkka', (SELECT id from silta WHERE siltanro = 902), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'));
INSERT INTO siltatarkastus (lahde, tarkastusaika, tarkastaja, silta, urakka) VALUES ('harja-ui'::lahde, '2008-06-25', 'Late Lujuuslaskija', (SELECT id from silta WHERE siltanro = 325235), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'));

INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos, lisatieto) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 1, 'A', 'Maatuki ruosteessa.');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 2, 'B');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 3, 'C');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 4, 'D');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 5, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 6, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 7, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 8, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 9, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 10, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 11, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 12, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 13, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 14, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 15, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 16, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 17, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 18, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 19, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 20, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 21, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 22, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 23, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2006-04-15'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 24, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 1, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 2, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 3, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 4, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 5, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 6, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 7, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 8, 'B');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 9, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 10, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 11, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 12, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 13, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 14, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 15, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 16, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 17, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 18, 'D');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 19, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 20, 'C');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 21, 'B');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 22, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 23, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-02-25'  AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 24, 'A');

INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 1, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 2, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 3, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 4, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 5, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 6, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 7, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 8, 'B');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 9, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 10, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 11, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 12, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 13, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 14, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 15, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 16, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 17, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 18, 'D');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 19, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 20, 'C');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 21, 'B');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 22, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 23, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2007-05-05' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 24, 'A');

INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 1, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 2, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 3, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 4, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 5, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 6, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 7, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 8, 'B');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 9, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 10, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 11, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 12, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 13, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 14, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 15, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 16, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 17, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 18, 'D');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 19, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 20, 'C');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 21, 'B');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 22, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 23, 'A');
INSERT INTO siltatarkastuskohde (siltatarkastus, kohde, tulos) VALUES ((SELECT id from siltatarkastus where tarkastusaika = '2008-06-25' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')), 24, 'A');


-- Luodaan Kajaanintien sillalle 2 tarkastusta, jossa jälkimmäinen korjaa ensimmäisessä havaittuja ongelmi
INSERT INTO siltatarkastus (lahde, tarkastusaika, tarkastaja, silta, urakka) VALUES ('harja-ui'::lahde, '2014-04-08', 'Samuel Siltanen', (SELECT id from silta WHERE siltanro = 7777), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'));
INSERT INTO siltatarkastus (lahde, tarkastusaika, tarkastaja, silta, urakka) VALUES ('harja-ui'::lahde, '2015-04-08', 'Kalermo Korjaaja', (SELECT id from silta WHERE siltanro = 7777), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'));

INSERT
  INTO siltatarkastuskohde (siltatarkastus, kohde, tulos)
SELECT (SELECT id from siltatarkastus where tarkastusaika = '2014-04-08' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')) as tark, kohde, 'B' FROM generate_series(1,24) kohde;

INSERT
  INTO siltatarkastuskohde (siltatarkastus, kohde, tulos)
SELECT (SELECT id from siltatarkastus where tarkastusaika = '2015-04-08' AND urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012')) as tark, kohde, 'A' FROM generate_series(1,24) kohde;




-- Siltojen insertoinnin jälkeen on päivitettävä materialisoitu näkymä
REFRESH MATERIALIZED VIEW sillat_alueurakoittain;
