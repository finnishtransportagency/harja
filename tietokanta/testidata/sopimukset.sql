INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Oulun alueurakka pääsopimus', '2005-10-01','2012-09-30','8H05228/01', (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Oulun alueurakka pääsopimus','2014-10-01','2019-09-30','2H16339/01', (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka, paasopimus) VALUES ('Oulun alueurakka lisäsopimus', '2005-10-01','2012-09-30','THII-12-28555', (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE sampoid='8H05228/01'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka, paasopimus) VALUES ('Oulun alueurakka lisäsopimus', '2014-10-01','2019-09-30','5H16339/01', (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE sampoid='2H16339/01'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Pudasjärvi pääsopimus', '2007-10-01','2012-09-30','3H05228/40', (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka, paasopimus) VALUES ('Pudasjärvi lisäsopimus', '2007-10-01','2012-09-30','9H143239/01', (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT id FROM sopimus WHERE sampoid='3H05228/40'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Porin pääsopimus', '2007-10-01','2012-09-30','4H05111/22', (SELECT id FROM urakka WHERE nimi='Porin alueurakka 2007-2012'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Muhoksen päällystyksen pääsopimus', '2007-06-01','2012-09-30','5H05228/10', (SELECT id FROM urakka WHERE nimi='Muhoksen päällystysurakka'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Utajärven päällystyksen pääsopimus', '2019-10-01','2023-09-30','5H05339/10', (SELECT id FROM urakka WHERE nimi='Utajärven päällystysurakka'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('YHA-päällystysurakka (sidottu) pääsopimus', '2014-10-01','2018-09-30','5H03228/10', (SELECT id FROM urakka WHERE nimi='YHA-päällystysurakka (sidottu)'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Oulun päällystyksen palvelusopimus, pääsopimus', '2016-01-01','2018-12-31','5H01228/10', (SELECT id FROM urakka WHERE nimi='Oulun päällystyksen palvelusopimus'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('PIR RATU IHJU 2016 -2022, P pääsopimus', '2016-01-01','2023-12-31','5H07428/10', (SELECT id FROM urakka WHERE nimi='PIR RATU IHJU 2016 -2022, P'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('KAS siltojen ylläpidon palvelusopimus Etelä-Karjala 2016-2019, P pääsopimus', '2015-01-01','2020-12-31','5H05728/10', (SELECT id FROM urakka WHERE nimi='KAS siltojen ylläpidon palvelusopimus Etelä-Karjala 2016-2019, P'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('YHA1 pääsopimus', '2014-06-01','2018-09-30','5HE5228/10', (SELECT id FROM urakka WHERE nimi='YHA-päällystysurakka'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('YHA2 pääsopimus', '2014-06-01','2018-09-30','5HE5558/10', (SELECT id FROM urakka WHERE nimi='YHA-paikkausurakka'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Muhoksen paikkauksen pääsopimus', '2007-06-01','2012-09-30','5H05229/10', (SELECT id FROM urakka WHERE nimi='Muhoksen paikkausurakka'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Pääsopimus', '2014-10-01','2016-09-30','5H05231/10', (SELECT id FROM urakka WHERE nimi='Päällysteiden paikkausurakka KAS ELY 2014-2016'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Pääsopimus', '2005-10-01','2006-09-30','5H05276/10', (SELECT id FROM urakka WHERE nimi='Tienpäällystysurakka KAS ELY 1 2015'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Porintien pääsopimus', '2007-05-01','2007-08-22','8605228/10', (SELECT id FROM urakka WHERE nimi='Porintien päällystysurakka'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Oulun tiemerkinnän palvelusopimuksen pääsopimus 2013-2018', '2013-01-01','2018-12-31','7H05228/10', (SELECT id FROM urakka WHERE nimi='Oulun tiemerkinnän palvelusopimus 2013-2018'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017', '2013-01-01','2017-12-31','7H01264/10', (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Oulun valaistuksen palvelusopimuksen pääsopimus 2013-2018', '2013-01-01','2018-12-31','5A05228/10', (SELECT id FROM urakka WHERE nimi='Oulun valaistuksen palvelusopimus 2013-2018'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020', '2015-01-01','2020-12-31','5A65228/10', (SELECT id FROM urakka WHERE nimi='Tievalaistuksen palvelusopimus 2015-2020'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Pirkanmaan tiemerkinnän palvelusopimuksen pääsopimus 2013-2018', '2013-01-01','2018-12-31','2A05228/10', (SELECT id FROM urakka WHERE nimi='Pirkanmaan tiemerkinnän palvelusopimus 2013-2018'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Lapin tiemerkinnän palvelusopimuksen pääsopimus 2013-2018', '2013-01-01','2018-12-31','2A06228/10', (SELECT id FROM urakka WHERE nimi='Lapin tiemerkinnän palvelusopimus 2013-2018'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Kempeleen valaistuksen pääsopimus', '2007-03-01','2012-05-30','9H05224/01', (SELECT id FROM urakka WHERE nimi='Kempeleen valaistusurakka'));
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka) VALUES ('Tampereen alueurakka pääsopimus','2017-10-01','2022-09-30','6A16339/01', (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'));

-- Kajaanin pääsopimus
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka)
VALUES
  ('Kajaanin alueurakka pääsopimus','2014-10-01','2019-09-30','7A26339/05', (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'));
-- Kajaanin lisäsopimus
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka, paasopimus)
VALUES
  ('Kajaanin alueurakka lisäsopimus','2014-10-01','2019-09-30','7lisa26339/06', (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019') AND paasopimus IS null));

-- Vantaan pääsopimus
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka)
VALUES
  ('Vantaan alueurakka pääsopimus','2009-10-01','2019-09-30','00LZM-0033600', (SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'));
-- Vantaan lisäsopimus
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka, paasopimus)
VALUES
  ('Vantaan alueurakka lisäsopimus','2009-10-01','2019-09-30','7lisaV26339/06', (SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019') AND paasopimus IS null));

-- Espoon pääsopimus
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka)
VALUES
  ('Espoon alueurakka pääsopimus','2014-10-01','2019-09-30','7eS6339/05', (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'));
-- Espoon lisäsopimus
INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka, paasopimus)
VALUES
  ('Espoon alueurakka lisäsopimus','2014-10-01','2019-09-30','7lisaES6339/06', (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null));
