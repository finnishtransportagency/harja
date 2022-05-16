-- Suunnittele hiekoitushiekan käyttöä Oulun urakkaan

INSERT INTO materiaalin_kaytto
(alkupvm, loppupvm, maara, materiaali, urakka, sopimus,luotu, luoja)
VALUES
  ('20171001', '20180930', 1000, (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka'),
   (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null), NOW(),
   (SELECT id FROM kayttaja WHERE kayttajanimi='jvh')),
  ('20171001', '20180930', 1000, (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka'),
   (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019') AND paasopimus IS null), NOW(),
   (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'));

-- Aseta talvisuolaraja ja suolasakko
INSERT INTO suolasakko (maara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka, talvisuolaraja)
VALUES (30.0, 2017, 8, 'MAKU 2005', (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 1000),
  (30.0, 2017, 8, 'MAKU 2005', (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 1000);

-- Pohjavesialueen käyttöraja kälitestausta varten
INSERT INTO pohjavesialue_talvisuola(pohjavesialue, urakka, hoitokauden_alkuvuosi, talvisuolaraja, tie)
VALUES ('11244001', 4, 2021, 100, 846);

-- Suolauksen toteuma (materiaalitoteuma) Ouluun
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto, luoja)
VALUES ('harja-api'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null),
        NOW(), '2018-02-15 13:00:00+02', '2018-02-15 13:00:00+02',
        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'YmpRap-toteuma', (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'));

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id, luoja)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'YmpRap-toteuma'), NOW(),
        (SELECT id FROM materiaalikoodi WHERE nimi='Talvisuola, rakeinen NaCl'), 1600, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'));

INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto, luoja)
VALUES ('harja-api'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null),
        NOW(), '2018-02-15 12:00:00+02', '2018-02-15 12:00:00+02',
        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'YmpRap-toteuma2', (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'));

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id, luoja)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'YmpRap-toteuma2'), NOW(),
        (SELECT id FROM materiaalikoodi WHERE nimi='Hiekoitushiekka'), 1000, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'));

-- Uudet talvihoitoluokat voimaan 2.7.2018 Tätä dataa tulkittava raporteissa uuden koodiston mukaisesti
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
VALUES ('harja-api'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null),
        NOW(), '2018-10-15 13:00:00+02', '2018-10-15 13:00:00+02',
        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'YmpRap-toteuma-uudet-talvihoitoluokat');

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'YmpRap-toteuma-uudet-talvihoitoluokat'), NOW(),
        (SELECT id FROM materiaalikoodi WHERE nimi='Talvisuola, rakeinen NaCl'), 1100, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'));

-- Kajaaniin
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
VALUES ('harja-api'::lahde, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019') AND paasopimus IS null),
        NOW(), '2018-02-15 13:00:00+02', '2018-02-15 13:00:00+02',
        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'YmpRap-toteuma3');

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'YmpRap-toteuma3'), NOW(),
        (SELECT id FROM materiaalikoodi WHERE nimi='Talvisuola, rakeinen NaCl'), 1000, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'));


INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
VALUES ('harja-api'::lahde, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019') AND paasopimus IS null),
        NOW(), '2018-02-15 12:00:00+02', '2018-02-15 12:00:00+02',
        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'YmpRap-toteuma4');

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'YmpRap-toteuma4'), NOW(),
        (SELECT id FROM materiaalikoodi WHERE nimi='Hiekoitushiekka'), 1000, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'));

-- Hoitoluokittaiset vastaavasti Ouluun ja Kajaaniin
INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain (pvm, materiaalikoodi, talvihoitoluokka, urakka, maara)
VALUES
  -- Talvisuolaa 1000t per urakka
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 0, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 300),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 1, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 2, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 3, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 4, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 5, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 6, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 7, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 8, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 9, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 100, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),

  -- uudet talvihoitoluokat
  ('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 1, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 2, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 3, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 4, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 5, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 6, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 7, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 8, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 9, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 10, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),
  ('2018-10-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 11, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 100),

  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 0, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 300),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 1, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 2, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 3, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 200),
  ('2018-02-15', (SELECT id from materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 4, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 100),

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
