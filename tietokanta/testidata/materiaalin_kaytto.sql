-- Suunnittele hiekoitushiekan käyttöä Oulun urakkaan

INSERT INTO materiaalin_kaytto
(alkupvm, loppupvm, maara, materiaali, urakka, sopimus,luotu, luoja)
VALUES
  ('20141001', '20150930', 800, (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka'),
   (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null), NOW(),
   (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'));

INSERT INTO materiaalin_kaytto
(alkupvm, loppupvm, maara, materiaali, urakka, sopimus,luotu, luoja)
VALUES
  ('20151001', '20160930', 800, (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka'),
   (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null), NOW(),
   (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'));

INSERT INTO materiaalin_kaytto
(alkupvm, loppupvm, maara, materiaali, urakka, sopimus,luotu, luoja)
VALUES
  ('20161001', '20170930', 800, (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka'),
   (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null), NOW(),
   (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'));

