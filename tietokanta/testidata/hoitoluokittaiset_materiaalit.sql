-- Suunnittele hiekoitushiekan käyttöä Oulun urakkaan

INSERT INTO materiaalin_kaytto
(alkupvm, loppupvm, maara, materiaali, urakka, sopimus,luotu, luoja)
VALUES
  ('20171001', '20180930', 1000, (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka'),
   (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null), NOW(),
   (SELECT id FROM kayttaja WHERE kayttajanimi='jvh')),
  ('20171001', '20180930', 1000, (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka'),
   (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019') AND paasopimus IS null), NOW(),
   (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'));

-- Aseta talvisuolaraja ja suolasakko
INSERT INTO suolasakko (maara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka, talvisuolaraja)
VALUES (30.0, 2017, 8, 'MAKU 2005', (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 1000),
  (30.0, 2017, 8, 'MAKU 2005', (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 1000);


-- Suolauksen toteuma (materiaalitoteuma) Ouluun
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null),
        NOW(), '2018-02-15 13:00:00+02', '2018-02-15 13:00:00+02',
        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'YmpRap-toteuma');

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'YmpRap-toteuma'), NOW(),
        (SELECT id FROM materiaalikoodi WHERE nimi='Talvisuola'), 1000);

INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null),
        NOW(), '2018-02-15 12:00:00+02', '2018-02-15 12:00:00+02',
        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'YmpRap-toteuma2');

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'YmpRap-toteuma2'), NOW(),
        (SELECT id FROM materiaalikoodi WHERE nimi='Hiekoitushiekka'), 1000);

-- Kajaaniin
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019') AND paasopimus IS null),
        NOW(), '2018-02-15 13:00:00+02', '2018-02-15 13:00:00+02',
        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'YmpRap-toteuma3');

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'YmpRap-toteuma3'), NOW(),
        (SELECT id FROM materiaalikoodi WHERE nimi='Talvisuola'), 1000);


INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019') AND paasopimus IS null),
        NOW(), '2018-02-15 12:00:00+02', '2018-02-15 12:00:00+02',
        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'YmpRap-toteuma4');

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'YmpRap-toteuma4'), NOW(),
        (SELECT id FROM materiaalikoodi WHERE nimi='Hiekoitushiekka'), 1000);

-- Hoitoluokittaiset vastaavasti Ouluun ja Kajaaniin
INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain (pvm, materiaalikoodi, talvihoitoluokka, urakka, maara)
VALUES
  -- Talvisuolaa 1000t per urakka
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola'), 0, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 300),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola'), 1, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola'), 2, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola'), 3, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola'), 4, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola'), 0, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 300),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola'), 1, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola'), 2, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola'), 3, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola'), 4, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 100),

  -- Hiekoitushiekkaa 1000t per urakka
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 0, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 300),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 1, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 2, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 3, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 4, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 0, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 300),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 1, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 2, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 3, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Hiekoitushiekka'), 4, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 100);