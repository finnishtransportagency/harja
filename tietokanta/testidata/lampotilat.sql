INSERT INTO lampotilat (urakka, alkupvm, loppupvm, keskilampotila, pitka_keskilampotila)
VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), '2006-10-01', '2007-09-30', -7.2, -9.0),
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), '2007-10-01', '2008-09-30', -11.2, -9.0),
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), '2009-10-01', '2010-09-30', -6.2, -9.0),
 ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2011-10-01', '2012-09-30', -8.2, -9.0),
 ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2007-10-01', '2008-09-30', -13.2, -9.0),
 ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2008-10-01', '2009-09-30', -5.2, -9.0),
 ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2009-10-01', '2010-09-30', -5.2, -9.0),
  ((SELECT id FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), '2009-10-01', '2010-09-30', 1.2, -3.0);
