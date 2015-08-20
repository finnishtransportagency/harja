-- Tämä tiedosto sisältää Oulun alueurakka 2014-2019 testidataa, jonka avulla voidaan luoda mielekäs laskutusyhteenveto
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus)
     VALUES (2014, 10, 3500, '2014-10-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
            (2014, 11, 3500, '2014-11-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
            (2014, 12, 3500, '2014-12-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 1, 3500, '2015-01-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 2, 3500, '2015-02-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 3, 3500, '2015-03-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 4, 3500, '2015-04-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 5, 3500, '2015-05-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 6, 3500, '2015-06-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 7, 3500, '2015-07-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 8, 3500, '2015-08-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 9, 3500, '2015-09-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus)
    VALUES ('2014-10-01', '2014-12-31', 3, 'km', 10, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Is 1-ajorat. KVL >15000'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
           ('2015-01-01', '2015-09-30', 9, 'km', 10, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Is 1-ajorat. KVL >15000'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
           ('2014-10-01', '2014-12-31', 60, 'ha', 100, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Vesakonraivaus'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
           ('2015-01-01', '2015-09-30', 180, 'ha', 100, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Vesakonraivaus'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
           ('2014-10-01', '2014-12-31', 100, 't', 100, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Sorastus'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null)),
           ('2015-01-01', '2015-09-30', 300, 't', 100, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Sorastus'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null));

-- Suolauksen sallittu määrä
INSERT INTO materiaalin_kaytto (alkupvm, loppupvm, maara, materiaali,
                                urakka, sopimus,
                                pohjavesialue, luotu, muokattu, luoja, muokkaaja, poistettu)
     VALUES ('20141001', '20150930', 800, (SELECT id FROM materiaalikoodi WHERE nimi='Talvisuolaliuos NaCl'),
             (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
             (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null),
             null, '2004-10-19 10:23:54+02', null, (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), null, false);

-- Suolauksen toteuma (materiaalitoteuma)
INSERT INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
     VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
             (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null),
             '2015-02-19 10:23:54+02', '2015-02-18 00:00:00+02', '2015-02-18 02:00:00+02',
             'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', 'Y123', 'LYV-toteuma');
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara)
    VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'LYV-toteuma'), '2015-02-19 10:23:54+02',
            (SELECT id FROM materiaalikoodi WHERE nimi='Talvisuolaliuos NaCl'), 1000);

-- Sydäntalven lämpötila hoitokaudella ja pitkän ajan keskiarvo, vaikuttaa sallittuun suolamäärään
INSERT INTO lampotilat (urakka, alkupvm, loppupvm, keskilampotila, pitka_keskilampotila)
     VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
            '2014-10-01', '2015-09-30', -6.2, -9.0);

-- Suolasakon suuruus ja sidottava indeksi
INSERT INTO suolasakko (maara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka)
     VALUES (30.0, 2014, 8, 'MAKU 2010', (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'));