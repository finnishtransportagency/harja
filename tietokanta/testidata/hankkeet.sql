-- Tehdään pari hanketta
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid) values ('Oulun alueurakka','2010-10-01', '2015-09-30', '1238', 'oulu1');
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid) values ('Pudasjärven alueurakka','2007-10-01', '2012-09-30', '1229', 'pudis2');
INSERT INTO hanke (nimi,alkupvm,loppupvm,alueurakkanro, sampoid) values ('Oulun alueurakka','2014-10-01', '2019-09-30', '1238', 'oulu2');

-- Liitetään urakat niihin
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='oulu1') WHERE tyyppi='hoito' AND nimi LIKE 'Oulun%2005%';
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='pudis2') WHERE tyyppi='hoito' AND nimi LIKE 'Pudas%';
UPDATE urakka SET hanke=(SELECT id FROM hanke WHERE sampoid='oulu2') WHERE tyyppi='hoito' AND nimi LIKE 'Oulun%2014%';
