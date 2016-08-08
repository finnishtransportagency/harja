-- Luodaan hankkeet

INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid) values ('Oulun alueurakka','2010-10-01', '2015-09-30', '1238', 'oulu1');
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid) values ('Pudasjärven alueurakka','2007-10-01', '2012-09-30', '1229', 'pudis2');
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid) values ('Oulun alueurakka','2014-10-01', '2019-09-30', '1238', 'oulu2');
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid) values ('Kajaanin alueurakka','2014-10-01', '2019-09-30', '1236', 'kaj1');
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid) values ('Vantaan alueurakka','2014-10-01', '2019-09-30', '131', 'van1');
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid) values ('Espoon alueurakka','2014-10-01', '2019-09-30', '130', 'esp1');
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid, sampo_tyypit)
values ('Muhoksen päällystysurakka', '2014-10-01','2018-09-30', '666', 'muho1', 'TYP');
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid, sampo_tyypit)
values ('Pirkanmaan tiemerkinnän palvelusopimus 2013-2018', '2013-01-01','2018-12-31', '667', 'tiem1', 'TYT');
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid, sampo_tyypit)
values ('Kempeleen valaistusurakka', '2007-10-01','2012-09-30', '668', 'valai1', 'TYV');

-- Liitetään urakat niihin

UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='oulu1') WHERE tyyppi='hoito' AND nimi LIKE 'Oulun%2005%';
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='pudis2') WHERE tyyppi='hoito' AND nimi LIKE 'Pudas%';
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='oulu2') WHERE tyyppi='hoito' AND nimi LIKE 'Oulun%2014%';
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='kaj1') WHERE tyyppi='hoito' AND nimi LIKE 'Kajaanin%2014%';
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='van1') WHERE tyyppi='hoito' AND nimi LIKE 'Vantaan%2014%';
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='esp1') WHERE tyyppi='hoito' AND nimi LIKE 'Espoon%2014%';
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='muho1') WHERE tyyppi='paallystys' AND nimi = 'Muhoksen päällystysurakka';
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='tiem1') WHERE tyyppi='tiemerkinta' AND nimi = 'Pirkanmaan tiemerkinnän palvelusopimus 2013-2018';
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='valai1') WHERE tyyppi='valaistus' AND nimi = 'Kempeleen valaistusurakka';