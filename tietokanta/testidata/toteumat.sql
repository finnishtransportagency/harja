-- Oulun alueurakka 2005-2010

INSERT INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), NOW(), '2005-10-01 00:00:00+02', '2005-10-02 00:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', 'Y123', 'Reitillinen yksikköhintainen toteuma 1');
INSERT INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), NOW(), '2005-10-01 00:00:00+02', '2005-10-02 00:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Antti Ahertaja', 'Y124', 'Yksikköhintainen toteuma 1');
INSERT INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), NOW(), '2005-10-02 00:00:00+02', '2005-10-03 00:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Teemu Työntekijä', 'Y124', 'Yksikköhintainen toteuma 2');
INSERT INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), NOW(), '2005-10-03 00:00:00+02', '2005-03-04 00:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Teppo Tienraivaaja', 'Y125', 'Kokonaishintainen toteuma 1');
INSERT INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), NOW(), '2005-10-03 00:00:00+02', '2005-10-04 00:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Ahti Ahkera', 'Y125', 'Kokonaishintainen toteuma 2');
INSERT INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto, luoja) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), '2004-10-19 10:23:54+02', '2004-10-20 00:00:00+02', '2006-09-30 00:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Pekan Kone OY', 'Y125', 'Automaattisesti lisätty fastroi toteuma', (SELECT id FROM kayttaja WHERE kayttajanimi = 'fastroi'));

INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Reitillinen yksikköhintainen toteuma 1'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Is 1-ajorat. KVL >15000'), 10);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Yksikköhintainen toteuma 1'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Is 1-ajorat. KVL >15000'), 15);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Yksikköhintainen toteuma 2'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Is 1-ajorat. KVL >15000'), 5);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Automaattisesti lisätty fastroi toteuma'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Is 1-ajorat. KVL >15000'), 28);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Automaattisesti lisätty fastroi toteuma'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Is ohituskaistat KVL >15000'), 123);
INSERT INTO toteuma_tehtava (toteuma, luotu,toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 1'), '2005-11-11 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Siltojen puhdistus'), 666);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 2'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Is ohituskaistat KVL >15000'), 150);

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara) VALUES (1, '2005-10-01 00:00.00', 1, 7);
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara) VALUES (1, '2005-10-01 00:00.00', 2, 4);
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara) VALUES (2, '2005-10-01 00:00.00', 3, 3);
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara) VALUES (3, '2005-10-01 00:00.00', 4, 9);
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara) VALUES ((SELECT id FROM toteuma WHERE luoja = 7), '2005-10-01 00:00.00', 5, 25);

INSERT INTO toteuma (urakka, sopimus, alkanut, paattynyt, tyyppi, lisatieto, suorittajan_ytunnus, suorittajan_nimi) VALUES ((SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2005-2010') LIMIT 1), '2005-11-01 14:00:00.000000', '2005-11-01 15:00:00.000000', 'yksikkohintainen', 'Varustetoteuma', '8765432-1', 'Tehotekijät Oy');
INSERT INTO varustetoteuma (tunniste, toteuma, toimenpide, tietolaji, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, tr_alkuetaisyys, piiri, kuntoluokka, luoja, luotu, karttapvm, tr_puoli, tr_ajorata, alkupvm, loppupvm, arvot, tierekisteriurakkakoodi) VALUES ('HARJ951547ZK', (SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma'), 'lisatty', 'tl505', 89, 1, null, null, 12, null, null, 9, '2015-11-05 11:57:05.360537', null, 1, null, null, null, 'HARJ951547ZK        2                           HARJ951547ZK          01  ', null);

-- Pudasjärven alueurakka 2007-2012
INSERT INTO toteuma
(urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, lisatieto, suorittajan_ytunnus, suorittajan_nimi, luoja)
VALUES
((SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
(SELECT id FROM sopimus WHERE nimi = 'Pudasjärvi pääsopimus'),
NOW(),
'2008-09-09 10:00.00',
'2008-09-09 10:09.00',
'kokonaishintainen'::toteumatyyppi,
'Tämä on käsin tekaistu juttu',
'Y1234',
'Antti Aurakuski',
(SELECT id FROM kayttaja WHERE kayttajanimi='jvh'));

INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, luoja, paivan_hinta, lisatieto)
VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Tämä on käsin tekaistu juttu'),
NOW(), 1350, 10, (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), 40, 'Tämä on tekaistu tehtävä');

-- Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017

INSERT INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ((SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017') AND paasopimus IS null), NOW(), '2013-11-01 00:00:00+02', '2013-11-01 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', 'Y123', 'KAS ELY 2013 - 2018 56');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 5);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 5);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 5);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 5);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 5);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 5);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 10);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 10);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 10);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 10);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 10);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 10);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Täristävät merkinnät: sylinterijyrsintä, reunaviiva, 2 ajr tie: lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm'), 25000);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Täristävät merkinnät: sylinterijyrsintä, keskiviiva, 1 ajr tie: lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm'), 30000);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Täristävät merkinnät: sylinterijyrsintä, reunaviiva, 1 ajr tie: lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm'), 5000);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Linjamerkinnän upotusjyrsintä'), 500);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Pyörätien jatkeet ja suojatiet'), 150);
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Sulkualueet'), 150);

INSERT INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ((SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017') AND paasopimus IS null), NOW(), '2013-11-01 00:00:00+02', '2013-11-01 10:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', 'Y123', 'KAS ELY 2013 - 2018 3');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 3'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Pyörätien jatkeet ja suojatiet'), 11037);
INSERT INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ((SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017') AND paasopimus IS null), NOW(), '2013-11-01 00:00:00+02', '2013-11-01 10:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', 'Y123', 'KAS ELY 2013 - 2018 4');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 4'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Sulkualueet'), 854);
INSERT INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ((SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017') AND paasopimus IS null), NOW(), '2013-11-01 00:00:00+02', '2013-11-01 10:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', 'Y123', 'KAS ELY 2013 - 2018 5');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 5'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Nuolet ja nopeusrajoitusmerkinnät ja väistämisviivat'), 2525);
INSERT INTO toteuma (urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ((SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017') AND paasopimus IS null), NOW(), '2013-11-01 00:00:00+02', '2013-11-01 10:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', 'Y123', 'KAS ELY 2013 - 2018 6');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 6'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Muut pienmerkinnät'), 595);

-- Muutos-, lisä- ja äkillistä hoitotytöätoteumatyyppi: 'akillinen-hoitotyo', 'lisatyo', 'muutostyo'
INSERT INTO toteuma (urakka, sopimus, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
    VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), '2005-11-11 00:00:00+02', '2005-11-11 00:00:00+02', 'akillinen-hoitotyo'::toteumatyyppi, 'Teppo Tienraivaaja', 'Y125', 'Äkillinen1'),
           ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), '2005-11-12 00:00:00+02', '2005-11-12 00:00:00+02', 'lisatyo'::toteumatyyppi, 'Teppo Tienraivaaja', 'Y125', 'Lisätyö1'),
           ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), '2005-11-15 00:00:00+02', '2005-11-15 00:00:00+02', 'vahinkojen-korjaukset'::toteumatyyppi, 'Teppo Tienraivaaja', 'Y125', 'Vahkorj1'),
           ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), '2005-11-13 00:00:00+02', '2005-11-13 00:00:00+02', 'muutostyo'::toteumatyyppi, 'Teppo Tienraivaaja', 'Y125', 'Muutostyö1'),
           ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), '2005-11-14 00:00:00+02', '2005-11-14 00:00:00+02', 'muutostyo'::toteumatyyppi, 'Teppo Tienraivaaja', 'Y125', 'Muutostyö2');
INSERT INTO toteuma (urakka, sopimus, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto, luoja) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010') AND paasopimus IS null), '2005-12-22 10:23:54+02', '2005-12-22 12:23:54+02', 'muutostyo'::toteumatyyppi, 'Pekan Kone OY', 'Y125', 'Koneen muutostyö1', (SELECT id FROM kayttaja WHERE kayttajanimi = 'fastroi'));
INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Äkillinen1'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Is 1-ajorat.'), 43);
INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Lisätyö1'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Is 2-ajorat.'), 4);
INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö1'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'I ohituskaistat'), 2);
INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Vahkorj1'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'I ohituskaistat'), 20);
INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara, paivan_hinta) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö2'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Ib ohituskaistat'), 3, 2000);
INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Koneen muutostyö1'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'I ohituskaistat'), 35.4);

-- Reittipisteet varustetoteumille

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma'),
'2008-09-09 13:04.00',
NOW(),
st_makepoint(498919, 7247099) ::POINT, 2);

-- Reittipisteet kokonaishintaiselle työlle

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Tämä on käsin tekaistu juttu'),
'2008-09-09 10:00.00',
NOW(),
st_makepoint(498919, 7247099) ::POINT, 2);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Tämä on käsin tekaistu juttu'),
'2008-09-09 10:03.00',
NOW(),
st_makepoint(499271, 7248395) ::POINT, 2);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Tämä on käsin tekaistu juttu'),
'2008-09-09 10:06.00',
NOW(),
st_makepoint(499399, 7249019) ::POINT, 2);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Tämä on käsin tekaistu juttu'),
'2008-09-09 10:09.00',
NOW(),
st_makepoint(499820, 7249885) ::POINT, 2);


INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 1'),
        '2005-11-11 10:01.00',
        NOW(),
        st_makepoint(440919, 7207099) ::POINT, 2);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 1'),
        '2005-11-11 10:03.51',
        NOW(),
        st_makepoint(440271, 7208395) ::POINT, 2);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 1'),
        '2005-11-11 10:06.00',
        NOW(),
        st_makepoint(440399, 7209019) ::POINT, 2);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 1'),
        '2005-11-11 10:09.00',
        NOW(),
        st_makepoint(440820, 7209885) ::POINT, 2);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Tällä kokonaishintaisella toteumalla on sijainti'),
'2005-10-10 10:00.00',
NOW(),
st_makepoint(498919, 7247099) ::POINT, 3);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Tällä kokonaishintaisella toteumalla on sijainti'),
'2005-10-10 10:00.00',
NOW(),
st_makepoint(499271, 7248395) ::POINT, 3);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Tällä kokonaishintaisella toteumalla on sijainti'),
'2005-10-10 10:00.00',
NOW(),
st_makepoint(499399, 7249019) ::POINT, 3);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Tällä kokonaishintaisella toteumalla on sijainti'),
'2005-10-10 10:00.00',
NOW(),
st_makepoint(499820, 7249885) ::POINT, 3);

-- Reittipisteet muutostyölle
-- Tämä paikka on suunnilleen Muhoksella en tarkastanut kartalta kovin tarkasti..

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö1'),
        '2005-11-13 00:03.00',
        NOW(),
        st_makepoint(453271, 7188395) ::POINT, 2);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö1'),
        '2005-11-13 00:06.00',
        NOW(),
        st_makepoint(453399, 7189019) ::POINT, 2);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö1'),
        '2005-11-13 00:09.00',
        NOW(),
        st_makepoint(453820, 7189885) ::POINT, 2);

INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES ((SELECT id FROM reittipiste WHERE aika = '2005-11-13 00:00.00' :: TIMESTAMP ),
        NOW(), 1350, 10);

INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES
  ((SELECT id FROM reittipiste WHERE aika = '2005-11-13 00:03.00' :: TIMESTAMP ),
   NOW(), 1350, 10);

INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES ((SELECT id FROM reittipiste WHERE aika = '2005-11-13 00:06.00' :: TIMESTAMP ),
        NOW(), 1350, 10);

INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES ((SELECT id FROM reittipiste WHERE aika = '2005-11-13 00:09.00' :: TIMESTAMP ),
        NOW(), 1350, 10);

-- Toinen muutoshintainen työ

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö2'),
        '2005-11-13 10:00.00',
        NOW(),
        st_makepoint(440919, 7207099) ::POINT, 2);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö2'),
        '2005-11-13 10:03.00',
        NOW(),
        st_makepoint(440271, 7208395) ::POINT, 2);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö2'),
        '2005-11-13 10:06.00',
        NOW(),
        st_makepoint(440399, 7209019) ::POINT, 2);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö2'),
        '2005-11-13 10:09.00',
        NOW(),
        st_makepoint(440820, 7209885) ::POINT, 2);

INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES ((SELECT id FROM reittipiste WHERE aika = '2005-11-13 10:00.00' :: TIMESTAMP ),
        NOW(), 1350, 10);

INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES
  ((SELECT id FROM reittipiste WHERE aika = '2005-11-13 10:03.00' :: TIMESTAMP ),
   NOW(), 1350, 10);

INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES ((SELECT id FROM reittipiste WHERE aika = '2005-11-13 10:06.00' :: TIMESTAMP ),
        NOW(), 1350, 10);

INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES ((SELECT id FROM reittipiste WHERE aika = '2005-11-13 10:09.00' :: TIMESTAMP ),
        NOW(), 1350, 10);

-- Reittipisteet yksikköhintaiselle työlle

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Reitillinen yksikköhintainen toteuma 1'),
'2005-10-10 10:00.00',
NOW(),
st_makepoint(498919, 7247099) ::POINT, 3);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Reitillinen yksikköhintainen toteuma 1'),
'2005-10-10 10:00.00',
NOW(),
st_makepoint(499271, 7248395) ::POINT, 3);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Reitillinen yksikköhintainen toteuma 1'),
'2005-10-10 10:00.00',
NOW(),
st_makepoint(499399, 7249019) ::POINT, 3);

INSERT INTO reittipiste (toteuma, aika, luotu, sijainti, talvihoitoluokka)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Reitillinen yksikköhintainen toteuma 1'),
'2005-10-10 10:00.00',
NOW(),
st_makepoint(499820, 7249885) ::POINT, 3);

INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES ((SELECT id FROM reittipiste WHERE aika = '2008-09-09 10:00.00' :: TIMESTAMP ),
NOW(), 1350, 10);

INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES
((SELECT id FROM reittipiste WHERE aika = '2008-09-09 10:03.00' :: TIMESTAMP ),
NOW(), 1350, 10);

INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES ((SELECT id FROM reittipiste WHERE aika = '2008-09-09 10:06.00' :: TIMESTAMP ),
NOW(), 1350, 10);

INSERT INTO reitti_tehtava (reittipiste, luotu, toimenpidekoodi, maara)
VALUES ((SELECT id FROM reittipiste WHERE aika = '2008-09-09 10:09.00' :: TIMESTAMP ),
NOW(), 1350, 10);