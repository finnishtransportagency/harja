INSERT INTO lampotilat (urakka, alkupvm, loppupvm,
                        keskilampotila, pitka_keskilampotila, pitka_keskilampotila_vanha)
VALUES
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), '2005-10-01', '2006-09-30', -6.2, -9.0, -9.3),
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), '2006-10-01', '2007-09-30', -7.2, -9.0, -9.3),
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), '2007-10-01', '2008-09-30', -11.2, -9.0, -9.3),
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), '2009-10-01', '2010-09-30', -6.2, -9.0, -9.3),
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), '2010-10-01', '2011-09-30', -6.6, -9.0, -9.3),
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), '2011-10-01', '2012-09-30', -8.2, -9.0, -9.3),
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2012-10-01', '2013-09-30', -9.1, -9.0, -9.3),
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2013-10-01', '2014-09-30', -4.9, -9.0, -9.3),
 ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2014-10-01', '2015-09-30', -6.2, -9.0, -9.3),
 ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2011-10-01', '2012-09-30', -8.2, -9.0, -9.3),
 ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2007-10-01', '2008-09-30', -13.2, -9.0, -9.3),
 ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2008-10-01', '2009-09-30', -5.2, -9.0, -9.3),
 ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2009-10-01', '2010-09-30', -5.2, -9.0, -9.3),
  ((SELECT id FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), '2009-10-01', '2010-09-30', 1.2, -3.0, -3.9);
