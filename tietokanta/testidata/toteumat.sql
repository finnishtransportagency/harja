-- Oulun alueurakka 2005-2012

INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto, luoja) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), NOW(), '2005-10-01 00:00:00+02', '2005-10-02 00:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'Reitillinen yksikköhintainen toteuma 1', (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), NOW(), '2005-10-01 00:00:00+02', '2005-10-02 00:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'Reitillinen yksikköhintainen toteuma 2');
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), NOW(), '2005-10-01 00:00:00+02', '2005-10-02 00:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'Yksikköhintainen toteuma 1');
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), NOW(), '2005-10-02 00:00:00+02', '2005-10-03 00:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Teemu Työntekijä', '1524792-1', 'Yksikköhintainen toteuma 2');
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), NOW(), '2005-10-01 00:00:00+02', '2005-03-02 00:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Teppo Tienraivaaja', '4715514-4', 'Kokonaishintainen toteuma 1');
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), NOW(), '2005-10-03 00:00:00+02', '2005-10-04 00:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Ahti Ahkera', '4715514-4', 'Kokonaishintainen toteuma 2');
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), NOW(), '2005-10-10 00:09:00+02', '2005-10-11 00:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Eero Energia', '4715514-4', 'Kokonaishintainen toteuma 3');
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), NOW(), '2005-10-10 00:12:00+02', '2005-10-10 12:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Matti Matala', '4715514-4', 'Kokonaishintainen toteuma 4');
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto, luoja) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), '2004-10-19 10:23:54+02', '2004-10-20 00:00:00+02', '2006-09-30 00:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Pekan Kone OY', '4715514-4', 'Automaattisesti lisätty fastroi toteuma', (SELECT id FROM kayttaja WHERE kayttajanimi = 'destia'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE nimi = 'Oulun alueurakka pääsopimus' AND urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019')), '2015-12-17 08:22:45.049545', '2015-02-01 17:00:00.000000', '2015-02-01 18:05:00.000000', '8765432-1', 'Tehotekijät Oy', 'kokonaishintainen', 'Pitkä kokonaishintainen reitti');
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Kajaanin alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE nimi = 'Kajaanin alueurakka pääsopimus' AND urakka = (SELECT id FROM urakka WHERE nimi = 'Kajaanin alueurakka 2014-2019')), '2015-12-17 08:22:45.049545', '2015-02-01 17:00:00.000000', '2015-02-01 18:05:00.000000', '8765432-1', 'Tehotekijät Oy', 'kokonaishintainen', 'Pitkä kokonaishintainen reitti 2');
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE nimi = 'Oulun alueurakka pääsopimus' AND urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019')), '2016-11-30 17:00:00.000000', '2016-11-30 17:00:00.000000', '2016-11-30 18:05:00.000000', '8765432-1', 'Tehotekijät Oy', 'yksikkohintainen', 'Yksikköhintainen marraskuun toteuma');
INSERT INTO toteuma (tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, lahde, urakka, sopimus, luotu, alkanut, paattynyt, suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto, reitti, envelope) VALUES (22,5,10964,8,241,'harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE nimi = 'Oulun alueurakka pääsopimus' AND urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019')), NOW(), '2017-02-01 17:00:00.000000', '2017-02-01 18:05:00.000000', '8765432-1', 'Tehotekijät Oy', 'kokonaishintainen', 'Muoniohommat', 'MULTILINESTRING((447671.541963537 7192520.02854536,447677.6951 7192494.1671,447688.3669 7192451.6102,447701.0678 7192395.6772,447714.1022 7192340.0657,447733.1923 7192258.9203,447748.7298 7192191.082,447768.9389 7192104.3312,447781.1282 7192052.8022,447800.6809 7191968.2859),(447800.6809 7191968.2859,447806.674 7191943.7519,447813.6212 7191914.0192,447817.6139 7191896.8302,447822.3902 7191876.346,447826.717 7191857.3977,447832.3039 7191834.5819,447844.2121 7191786.001,447854.5182 7191743.2702,447856.871542228 7191733.93248052))'::GEOMETRY, st_envelope('MULTILINESTRING((447671.541963537 7192520.02854536,447677.6951 7192494.1671,447688.3669 7192451.6102,447701.0678 7192395.6772,447714.1022 7192340.0657,447733.1923 7192258.9203,447748.7298 7192191.082,447768.9389 7192104.3312,447781.1282 7192052.8022,447800.6809 7191968.2859),(447800.6809 7191968.2859,447806.674 7191943.7519,447813.6212 7191914.0192,447817.6139 7191896.8302,447822.3902 7191876.346,447826.717 7191857.3977,447832.3039 7191834.5819,447844.2121 7191786.001,447854.5182 7191743.2702,447856.871542228 7191733.93248052))'::GEOMETRY));

INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Reitillinen yksikköhintainen toteuma 1'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), 10, (SELECT urakka FROM toteuma WHERE lisatieto = 'Reitillinen yksikköhintainen toteuma 1'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Reitillinen yksikköhintainen toteuma 2'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pensaiden poisto'), 7, (SELECT urakka FROM toteuma WHERE lisatieto = 'Reitillinen yksikköhintainen toteuma 2'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Yksikköhintainen toteuma 1'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), 15, (SELECT urakka FROM toteuma WHERE lisatieto = 'Yksikköhintainen toteuma 1'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Yksikköhintainen toteuma 2'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), 5, (SELECT urakka FROM toteuma WHERE lisatieto = 'Yksikköhintainen toteuma 2'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Automaattisesti lisätty fastroi toteuma'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), 28, (SELECT urakka FROM toteuma WHERE lisatieto = 'Automaattisesti lisätty fastroi toteuma'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Automaattisesti lisätty fastroi toteuma'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pensaiden poisto'), 123, (SELECT urakka FROM toteuma WHERE lisatieto = 'Automaattisesti lisätty fastroi toteuma'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 1'), '2005-11-11 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Sorastus'), 666, (SELECT urakka FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 1'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 2'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pistehiekoitus'), 150, (SELECT urakka FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 2'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 2'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Sorastus'), 6, (SELECT urakka FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 2'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 3'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pistehiekoitus'), 98, (SELECT urakka FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 3'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 4'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pistehiekoitus'), 12, (SELECT urakka FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 4'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Pitkä kokonaishintainen reitti'), '2015-12-17 08:22:45.049545', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Suolaus'), 123, (SELECT urakka FROM toteuma WHERE lisatieto = 'Pitkä kokonaishintainen reitti'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Pitkä kokonaishintainen reitti 2'), '2015-12-17 08:22:45.049545', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Suolaus'), 123, (SELECT urakka FROM toteuma WHERE lisatieto = 'Pitkä kokonaishintainen reitti 2'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Muoniohommat'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Suolaus'), 666, (SELECT urakka FROM toteuma WHERE lisatieto = 'Muoniohommat'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Yksikköhintainen marraskuun toteuma'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), 15, (SELECT urakka FROM toteuma WHERE lisatieto = 'Yksikköhintainen marraskuun toteuma'));

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id) VALUES (1, '2005-10-01 00:00.00', 1, 7, (SELECT urakka FROM toteuma WHERE lisatieto = 'Yksikköhintainen marraskuun toteuma'));
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id) VALUES (1, '2005-10-01 00:00.00', 2, 4, (SELECT urakka FROM toteuma WHERE lisatieto = 'Yksikköhintainen marraskuun toteuma'));
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id) VALUES (2, '2005-10-01 00:00.00', 3, 3, (SELECT urakka FROM toteuma WHERE lisatieto = 'Yksikköhintainen marraskuun toteuma'));
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id) VALUES (3, '2005-10-01 00:00.00', 4, 9, (SELECT urakka FROM toteuma WHERE lisatieto = 'Yksikköhintainen marraskuun toteuma'));
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Automaattisesti lisätty fastroi toteuma'), '2005-10-01 00:00.00', 5, 25, (SELECT urakka FROM toteuma WHERE lisatieto = 'Yksikköhintainen marraskuun toteuma'));

INSERT INTO toteuma (lahde, urakka, sopimus, alkanut, paattynyt, tyyppi, lisatieto, suorittajan_ytunnus, suorittajan_nimi, reitti) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2005-2012') LIMIT 1), '2005-11-01 14:00:00.000000', '2005-11-01 15:00:00.000000', 'yksikkohintainen', 'Varustetoteuma', '8765432-1', 'Tehotekijät Oy', ST_ForceCollection(ST_COLLECT(ARRAY['POINT(442588 7227977)'])));
INSERT INTO varustetoteuma (tunniste, toteuma, toimenpide, tietolaji, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, tr_alkuetaisyys, piiri, kuntoluokka, luoja, luotu, karttapvm, tr_puoli, tr_ajorata, alkupvm, loppupvm, arvot, tierekisteriurakkakoodi, sijainti) VALUES (('HARJ000000000000000' || (SELECT nextval('livitunnisteet'))), (SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma'), 'lisatty', 'tl506', 89, 1, null, null, 12, null, null, 9, '2015-11-05 11:57:05.360537', null, 1, null, '2016-01-01', null, '111 2     01011                                                                                                                                                                         HARJ00000000000000012100   426939 7212766       ', null, st_geometryfromtext('POINT(442588 7227977)'));

-- Oulun alueurakka 2014-2019
-- Varustetoteumat

INSERT INTO toteuma (lahde, urakka, sopimus, alkanut, paattynyt, tyyppi, lisatieto, suorittajan_ytunnus, suorittajan_nimi, reitti) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019' AND paasopimus IS NULL)), '2016-01-01 14:00:00.000000', '2016-01-01 15:00:00.000000', 'yksikkohintainen', 'Varustetoteuma 4', '8765432-1', 'Tehotekijät Oy', ST_ForceCollection(ST_COLLECT(ARRAY['POINT(442588 7227977)'])));
INSERT INTO varustetoteuma (tunniste, toteuma, toimenpide, tietolaji, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, tr_alkuetaisyys, piiri, kuntoluokka, luoja, luotu, karttapvm, tr_puoli, tr_ajorata, alkupvm, loppupvm, arvot, tierekisteriurakkakoodi, sijainti) VALUES ('HARJ0000000000000001', (SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma 4'), 'lisatty', 'tl506', 89, 1, null, null, 12, null, 3, 9, '2015-11-05 11:57:05.360537', null, 1, null, '2016-01-01', null, '111 2     01011                                                                                                                                                                         HARJ00000000000000012100   426939 7212766       ', null, st_geometryfromtext('POINT(442588 7227977)'));

INSERT INTO toteuma (lahde, urakka, sopimus, alkanut, paattynyt, tyyppi, lisatieto, suorittajan_ytunnus, suorittajan_nimi, reitti) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019' AND paasopimus IS NULL)), '2016-01-02 12:03:00.000000', '2016-01-01 12:06:00.000000', 'kokonaishintainen', 'Varustetoteuma 5', '8765432-1', 'Tehotekijät Oy', ST_ForceCollection(ST_COLLECT(ARRAY['POINT(422588 7127977)'])));
INSERT INTO varustetoteuma (tunniste, toteuma, toimenpide, tietolaji, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, tr_alkuetaisyys, piiri, kuntoluokka, luoja, luotu, karttapvm, tr_puoli, tr_ajorata, alkupvm, loppupvm, arvot, tierekisteriurakkakoodi, sijainti) VALUES ('HARJ0000000000000001', (SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma 5'), 'paivitetty', 'tl506', 89, 1, null, null, 12, null, 5, 9, '2015-11-05 11:57:05.360537', null, 1, null, '2016-01-01', null, '111 2     01011                                                                                                                                                                         HARJ00000000000000012100   426939 7212766       ', null, st_geometryfromtext('POINT(422588 7127977)'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma 5'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), 666, (SELECT urakka FROM toteuma WHERE lisatieto = 'Varustetoteuma 5'));

INSERT INTO toteuma (lahde, urakka, sopimus, alkanut, paattynyt, tyyppi, lisatieto, suorittajan_ytunnus, suorittajan_nimi, reitti) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019' AND paasopimus IS NULL)), '2016-01-03 13:10:00.000000', '2016-01-01 13:10:00.000000', 'yksikkohintainen', 'Varustetoteuma 6', '8765432-1', 'Tehotekijät Oy', ST_ForceCollection(ST_COLLECT(ARRAY['POINT(443588 7226977)'])));
INSERT INTO varustetoteuma (tunniste, toteuma, toimenpide, tietolaji, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, tr_alkuetaisyys, piiri, kuntoluokka, luoja, luotu, karttapvm, tr_puoli, tr_ajorata, alkupvm, loppupvm, arvot, tierekisteriurakkakoodi, sijainti) VALUES ('HARJ0000000000000001', (SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma 6'), 'poistettu', 'tl506', 4, 364, null, null, 5322, null, 4, 9, '2015-11-05 11:57:05.360537', null, 1, null, '2016-01-01', null, '111 2     01011                                                                                                                                                                         HARJ00000000000000012100   426939 7212766       ', null, st_geometryfromtext('POINT(443588 7226977)'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma 6'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), 667, (SELECT urakka FROM toteuma WHERE lisatieto = 'Varustetoteuma 6'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma 6'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pensaiden täydennysistutus'), 668, (SELECT urakka FROM toteuma WHERE lisatieto = 'Varustetoteuma 6'));

INSERT INTO toteuma (lahde, urakka, sopimus, alkanut, paattynyt, tyyppi, lisatieto, suorittajan_ytunnus, suorittajan_nimi, reitti) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019' AND paasopimus IS NULL)), '2016-01-05 13:10:00.000000', '2016-01-05 13:20:00.000000', 'yksikkohintainen', 'Varustetarkastus 1', '8765432-1', 'Tehotekijät Oy', ST_ForceCollection(ST_COLLECT(ARRAY['POINT(445588 7127977)'])));
INSERT INTO varustetoteuma (tunniste, toteuma, toimenpide, tietolaji, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
                            piiri, kuntoluokka, luoja, luotu, karttapvm, tr_puoli, tr_ajorata, alkupvm, loppupvm, arvot, tierekisteriurakkakoodi, sijainti)
VALUES ('HARJ0000000000000001', (SELECT id FROM toteuma WHERE lisatieto = 'Varustetarkastus 1'), 'tarkastus', 'tl506', 4, 364, 4157, null, null, null, 5, 9, '2015-11-05 11:57:05.360537', null, 1, null, '2016-01-01', null, '111 2     01011                                                                                                                                                                         HARJ00000000000000012100   426939 7212766       ', null, st_geometryfromtext('POINT(445588 7127977)'));

INSERT INTO toteuma (lahde, urakka, sopimus, alkanut, paattynyt, tyyppi, lisatieto, suorittajan_ytunnus, suorittajan_nimi, reitti) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2005-2012') LIMIT 1), '2005-11-02 14:00:00.000000', '2005-11-01 15:00:00.000000', 'kokonaishintainen', 'Varustetoteuma 2', '8765432-1', 'Tehotekijät Oy', ST_ForceCollection(ST_COLLECT(ARRAY['POINT(422588 7127977)'])));
INSERT INTO varustetoteuma (tunniste, toteuma, toimenpide, tietolaji, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, tr_alkuetaisyys, piiri, kuntoluokka, luoja, luotu, karttapvm, tr_puoli, tr_ajorata, alkupvm, loppupvm, arvot, tierekisteriurakkakoodi, sijainti) VALUES ('HARJ0000000000000001', (SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma 2'), 'paivitetty', 'tl506', 89, 1, null, null, 12, null, null, 9, '2015-11-05 11:57:05.360537', null, 1, null, '2016-01-01', null, '111 2     01011                                                                                                                                                                         HARJ00000000000000012100   426939 7212766       ', null, st_geometryfromtext('POINT(422588 7127977)'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma 2'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), 666, (SELECT urakka FROM toteuma WHERE lisatieto = 'Varustetoteuma 2'));

INSERT INTO toteuma (lahde, urakka, sopimus, alkanut, paattynyt, tyyppi, lisatieto, suorittajan_ytunnus, suorittajan_nimi, reitti) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2005-2012') LIMIT 1), '2005-11-03 14:00:00.000000', '2005-11-01 15:00:00.000000', 'yksikkohintainen', 'Varustetoteuma 3', '8765432-1', 'Tehotekijät Oy', ST_ForceCollection(ST_COLLECT(ARRAY['POINT(445588 7237977)'])));
INSERT INTO varustetoteuma (tunniste, toteuma, toimenpide, tietolaji, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, tr_alkuetaisyys, piiri, kuntoluokka, luoja, luotu, karttapvm, tr_puoli, tr_ajorata, alkupvm, loppupvm, arvot, tierekisteriurakkakoodi, sijainti) VALUES ('HARJ0000000000000001', (SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma 3'), 'poistettu', 'tl506', 89, 1, null, null, 12, null, null, 9, '2015-11-05 11:57:05.360537', null, 1, null, '2016-01-01', null, '111 2     01011                                                                                                                                                                         HARJ00000000000000012100   426939 7212766       ', null, st_geometryfromtext('POINT(445588 7237977)'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma 3'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), 667, (SELECT urakka FROM toteuma WHERE lisatieto = 'Varustetoteuma 3'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Varustetoteuma 3'), '2005-10-01 00:00.00', (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pensaiden täydennysistutus'), 668, (SELECT urakka FROM toteuma WHERE lisatieto = 'Varustetoteuma 3'));

-- Materiaalitoteumat
INSERT INTO toteuma (lahde, urakka,sopimus,alkanut,paattynyt,tyyppi,luoja,lisatieto) VALUES (
  'harja-ui'::lahde,
  (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
  (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019') and paasopimus is null),
  '2016-03-21 12:00', '2016-03-21 13:00',
  'kokonaishintainen',
  (SELECT id FROM kayttaja WHERE jarjestelma IS NOT TRUE LIMIT 1),
  'Tämä on käyttäjän UI:lta luoma materiaalitoteuma');
INSERT INTO toteuma_materiaali (toteuma, materiaalikoodi, maara, urakka_id) VALUES (
  (SELECT MAX(id) FROM toteuma),
  (SELECT id FROM materiaalikoodi WHERE nimi='Hiekoitushiekka'),
  500, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'));
INSERT INTO toteuma (lahde, urakka,sopimus,alkanut,paattynyt,tyyppi,luoja,lisatieto) VALUES (
  'harja-ui'::lahde,
  (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
  (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019') and paasopimus is null),
  '2016-03-21 12:00', '2016-03-21 13:00',
  'kokonaishintainen',
  (SELECT id FROM kayttaja WHERE jarjestelma IS TRUE LIMIT 1),
  'Tämä on järjestelmän luoma materiaalitoteuma');
INSERT INTO toteuma_materiaali (toteuma, materiaalikoodi, maara, urakka_id) VALUES (
  (SELECT MAX(id) FROM toteuma),
  (SELECT id FROM materiaalikoodi WHERE nimi='Hiekoitushiekka'),
  500, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'));
INSERT INTO toteuma_materiaali (toteuma, materiaalikoodi, maara, urakka_id) VALUES (
  (SELECT MAX(id) FROM toteuma),
  (SELECT id FROM materiaalikoodi WHERE nimi='Hiekoitushiekka'),
  555, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'));
INSERT INTO toteuma (lahde, urakka,sopimus,alkanut,paattynyt,tyyppi,luoja,lisatieto) VALUES (
  'harja-ui'::lahde,
  (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'),
  (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019') and paasopimus is null),
  '2016-03-21 12:00', '2016-03-21 13:00',
  'kokonaishintainen',
  (SELECT id FROM kayttaja WHERE jarjestelma IS TRUE LIMIT 1),
  'Tämä on niin ikään järjestelmän luoma materiaalitoteuma');
INSERT INTO toteuma_materiaali (toteuma, materiaalikoodi, maara, urakka_id) VALUES (
  (SELECT MAX(id) FROM toteuma),
  (SELECT id FROM materiaalikoodi WHERE nimi='Hiekoitushiekka'),
  666, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'));

-- Pudasjärven alueurakka 2007-2012

INSERT INTO toteuma
(lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, lisatieto, suorittajan_ytunnus, suorittajan_nimi, luoja)
VALUES
('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
(SELECT id FROM sopimus WHERE nimi = 'Pudasjärvi pääsopimus'),
NOW(),
'2008-09-09 10:00.00',
'2008-09-09 10:09.00',
'kokonaishintainen'::toteumatyyppi,
'Tämä on käsin tekaistu juttu',
'9184629-5',
'Antti Aurakuski',
(SELECT id FROM kayttaja WHERE kayttajanimi='jvh'));

INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, luoja, paivan_hinta, lisatieto, urakka_id)
VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'Tämä on käsin tekaistu juttu'),
NOW(), 1350, 10, (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), 40, 'Tämä on tekaistu tehtävä', (SELECT urakka FROM toteuma WHERE lisatieto = 'Tämä on käsin tekaistu juttu'));

-- Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017

INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017') AND paasopimus IS null), NOW(), '2013-11-01 00:00:00+02', '2013-11-01 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'KAS ELY 2013 - 2018 56');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 5, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 5, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 5, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 5, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 5, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 5, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 10, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 10, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 10, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 10, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 10, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), 10, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Täristävät merkinnät: sylinterijyrsintä, reunaviiva, 2 ajr tie: lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm'), 25000, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Täristävät merkinnät: sylinterijyrsintä, keskiviiva, 1 ajr tie: lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm'), 30000, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Täristävät merkinnät: sylinterijyrsintä, reunaviiva, 1 ajr tie: lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm'), 5000, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Linjamerkinnän upotusjyrsintä'), 500, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Pyörätien jatkeet ja suojatiet'), 150, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Sulkualueet'), 150, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 56'));

INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017') AND paasopimus IS null), NOW(), '2013-11-01 00:00:00+02', '2013-11-01 10:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'KAS ELY 2013 - 2018 3');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 3'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Pyörätien jatkeet ja suojatiet'), 11037, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 3'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017') AND paasopimus IS null), NOW(), '2013-11-01 00:00:00+02', '2013-11-01 10:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'KAS ELY 2013 - 2018 4');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 4'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Sulkualueet'), 854, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 4'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017') AND paasopimus IS null), NOW(), '2013-11-01 00:00:00+02', '2013-11-01 10:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'KAS ELY 2013 - 2018 5');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 5'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Nuolet ja nopeusrajoitusmerkinnät ja väistämisviivat'), 2525, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 5'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017') AND paasopimus IS null), NOW(), '2013-11-01 00:00:00+02', '2013-11-01 10:00:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'KAS ELY 2013 - 2018 6');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES
((SELECT id FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 6'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Muut pienmerkinnät'), 595, (SELECT urakka FROM toteuma WHERE lisatieto = 'KAS ELY 2013 - 2018 6'));

-- VMT -raporttia varten
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022') AND paasopimus IS null), NOW(), '2020-11-01 00:00:00+02', '2020-11-01 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'TRE BLK SSG3 1');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'TRE BLK SSG3 1'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Päällystettyjen teiden palteiden poisto' and poistettu = false), 11, (SELECT urakka FROM toteuma WHERE lisatieto = 'TRE BLK SSG3 1'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'TRE BLK SSG3 2');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'TRE BLK SSG3 2'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Päällystettyjen teiden palteiden poisto' and poistettu = false), 11, (SELECT urakka FROM toteuma WHERE lisatieto = 'TRE BLK SSG3 2'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022') AND paasopimus IS null), NOW(), '2020-11-01 00:00:00+02', '2020-11-01 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'TRE BLK SSG3 1-2');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'TRE BLK SSG3 1-2'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Rumpujen korjaus ja uusiminen  600 - 1000 mm' and poistettu = false), 11, (SELECT urakka FROM toteuma WHERE lisatieto = 'TRE BLK SSG3 1-2'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'TRE BLK SSG3 2-2');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'TRE BLK SSG3 2-2'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Rumpujen korjaus ja uusiminen  600 - 1000 mm' and poistettu = false), 11, (SELECT urakka FROM toteuma WHERE lisatieto = 'TRE BLK SSG3 2-2'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022') AND paasopimus IS null), NOW(), '2020-11-01 00:00:00+02', '2020-11-01 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'TRE BLK SSG3 3');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'TRE BLK SSG3 3'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Kaivojen ja putkistojen sulatus' and poistettu = false), 11, (SELECT urakka FROM toteuma WHERE lisatieto = 'TRE BLK SSG3 3'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022') AND paasopimus IS null), NOW(), '2020-11-01 00:00:00+02', '2020-11-01 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'TRE BLK SSG3 4');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'TRE BLK SSG3 4'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -vanhan viitan/opastetaulun uusiminen' and poistettu = false), 11, (SELECT urakka FROM toteuma WHERE lisatieto = 'TRE BLK SSG3 4'));

INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2020-11-11 00:00:00+02', '2020-11-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'OUL TRI PLSI 1');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'OUL TRI PLSI 1'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Päällystettyjen teiden sorapientareen kunnossapito' and poistettu = false), 9, (SELECT urakka FROM toteuma WHERE lisatieto = 'OUL TRI PLSI 1'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'OUL TRI PLSI 1-2');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'OUL TRI PLSI 1-2'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Päällystettyjen teiden sorapientareen kunnossapito' and poistettu = false), 9, (SELECT urakka FROM toteuma WHERE lisatieto = 'OUL TRI PLSI 1-2'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2020-11-11 00:00:00+02', '2020-11-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'OUL TRI PLSI 2-1');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'OUL TRI PLSI 2-1'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Puun poisto raivausjätteineen (taajamassa)' and poistettu = false), 9, (SELECT urakka FROM toteuma WHERE lisatieto = 'OUL TRI PLSI 2-1'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'OUL TRI PLSI 2-2');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'OUL TRI PLSI 2-2'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Puun poisto raivausjätteineen (taajamassa)' and poistettu = false), 9, (SELECT urakka FROM toteuma WHERE lisatieto = 'OUL TRI PLSI 2-2'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'OUL TRI PLSI 3');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'OUL TRI PLSI 3'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Portaiden talvihoito' and poistettu = false), 9, (SELECT urakka FROM toteuma WHERE lisatieto = 'OUL TRI PLSI 3'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'OUL TRI PLSI 4');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'OUL TRI PLSI 4'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pensaiden täydennysistutus' and poistettu = false), 9, (SELECT urakka FROM toteuma WHERE lisatieto = 'OUL TRI PLSI 4'));

INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024') AND paasopimus IS null), NOW(), '2020-11-11 00:00:00+02', '2020-11-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'OUL PSK KPNN 1');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'OUL PSK KPNN 1'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Graffitien poisto' and poistettu = false), 15, (SELECT urakka FROM toteuma WHERE lisatieto = 'OUL PSK KPNN 1'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'OUL PSK KPNN 1-2');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'OUL PSK KPNN 1-2'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Graffitien poisto' and poistettu = false), 15, (SELECT urakka FROM toteuma WHERE lisatieto = 'OUL PSK KPNN 1-2'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024') AND paasopimus IS null), NOW(), '2020-11-11 00:00:00+02', '2020-11-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'OUL PSK KPNN 2-1');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'OUL PSK KPNN 2-1'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pysäkkikatosten puhdistus' and poistettu = false), 15, (SELECT urakka FROM toteuma WHERE lisatieto = 'OUL PSK KPNN 2-1'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'OUL PSK KPNN 2-2');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'OUL PSK KPNN 2-2'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pysäkkikatosten puhdistus' and poistettu = false), 15, (SELECT urakka FROM toteuma WHERE lisatieto = 'OUL PSK KPNN 2-2'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'OUL PSK KPNN 3');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'OUL PSK KPNN 3'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Ic 1-ajorat' and poistettu = false), 15, (SELECT urakka FROM toteuma WHERE lisatieto = 'OUL PSK KPNN 3'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'OUL PSK KPNN 4');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'OUL PSK KPNN 4'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Rumpujen korjaus ja uusiminen <= 600 mm' and poistettu = false), 15, (SELECT urakka FROM toteuma WHERE lisatieto = 'OUL PSK KPNN 4'));

INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Ivalon MHU testiurakka (uusi)'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Ivalon MHU testiurakka (uusi)') AND paasopimus IS null), NOW(), '2020-11-11 00:00:00+02', '2020-11-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'IVL EVL WHT1');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'IVL EVL WHT1'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Ib rampit' and poistettu = false), 8, (SELECT urakka FROM toteuma WHERE lisatieto = 'IVL EVL WHT1'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Ivalon MHU testiurakka (uusi)'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Ivalon MHU testiurakka (uusi)') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'IVL EVL WHT1-2');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'IVL EVL WHT1-2'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Ib rampit' and poistettu = false), 8, (SELECT urakka FROM toteuma WHERE lisatieto = 'IVL EVL WHT1-2'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Ivalon MHU testiurakka (uusi)'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Ivalon MHU testiurakka (uusi)') AND paasopimus IS null), NOW(), '2020-11-11 00:00:00+02', '2020-11-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'IVL EVL WHT2-1');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'IVL EVL WHT2-1'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'K2' and poistettu = false), 8, (SELECT urakka FROM toteuma WHERE lisatieto = 'IVL EVL WHT2-1'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Ivalon MHU testiurakka (uusi)'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Ivalon MHU testiurakka (uusi)') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'IVL EVL WHT2-2');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'IVL EVL WHT2-2'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'K2' and poistettu = false), 8, (SELECT urakka FROM toteuma WHERE lisatieto = 'IVL EVL WHT2-2'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Ivalon MHU testiurakka (uusi)'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Ivalon MHU testiurakka (uusi)') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'IVL EVL WHT3');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'IVL EVL WHT3'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Ic 1-ajorat' and poistettu = false), 8, (SELECT urakka FROM toteuma WHERE lisatieto = 'IVL EVL WHT3'));
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Ivalon MHU testiurakka (uusi)'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Ivalon MHU testiurakka (uusi)') AND paasopimus IS null), NOW(), '2021-01-11 00:00:00+02', '2021-01-11 10:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'IVL EVL WHT4');
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'IVL EVL WHT4'), NOW(), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Is 1-ajorat.' and poistettu = false), 8, (SELECT urakka FROM toteuma WHERE lisatieto = 'IVL EVL WHT4'));
-- Muutos-, lisä- ja äkillistä hoitotytöätoteumatyyppi: 'akillinen-hoitotyo', 'lisatyo', 'muutostyo'

INSERT INTO toteuma (lahde, urakka, sopimus, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
    VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), '2005-11-11 00:00:00+02', '2005-11-11 00:00:00+02', 'akillinen-hoitotyo'::toteumatyyppi, 'Teppo Tienraivaaja', '4715514-4', 'Äkillinen1'),
           ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), '2005-11-12 00:00:00+02', '2005-11-12 00:00:00+02', 'lisatyo'::toteumatyyppi, 'Teppo Tienraivaaja', '4715514-4', 'Lisätyö1'),
           ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), '2005-11-15 00:00:00+02', '2005-11-15 00:00:00+02', 'vahinkojen-korjaukset'::toteumatyyppi, 'Teppo Tienraivaaja', '4715514-4', 'Vahkorj1'),
           ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), '2005-11-13 00:00:00+02', '2005-11-13 00:00:00+02', 'muutostyo'::toteumatyyppi, 'Teppo Tienraivaaja', '4715514-4', 'Muutostyö1'),
           ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), '2005-11-14 00:00:00+02', '2005-11-14 00:00:00+02', 'muutostyo'::toteumatyyppi, 'Teppo Tienraivaaja', '4715514-4', 'Muutostyö2');
INSERT INTO toteuma (lahde, urakka, sopimus, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto, luoja) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), '2005-12-22 10:23:54+02', '2005-12-22 12:23:54+02', 'muutostyo'::toteumatyyppi, 'Pekan Kone OY', '4715514-4', 'Koneen muutostyö1', (SELECT id FROM kayttaja WHERE kayttajanimi = 'destia'));
INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Äkillinen1'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Is 1-ajorat.'), 43, (SELECT urakka FROM toteuma WHERE lisatieto = 'Äkillinen1'));
INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Lisätyö1'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Is 2-ajorat.'), 4, (SELECT urakka FROM toteuma WHERE lisatieto = 'Lisätyö1'));
INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö1'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'I ohituskaistat'), 2, (SELECT urakka FROM toteuma WHERE lisatieto = 'Muutostyö1'));
INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Vahkorj1'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'I ohituskaistat'), 20, (SELECT urakka FROM toteuma WHERE lisatieto = 'Vahkorj1'));
INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara, paivan_hinta, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö2'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Ib ohituskaistat'), 3, 2000, (SELECT urakka FROM toteuma WHERE lisatieto = 'Muutostyö2'));
INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Koneen muutostyö1'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'I ohituskaistat'), 35.4, (SELECT urakka FROM toteuma WHERE lisatieto = 'Koneen muutostyö1'));

-- Reittipisteet kokonaishintaiselle työlle

INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet) VALUES (
 (SELECT id FROM toteuma WHERE lisatieto = 'Tämä on käsin tekaistu juttu'),
 ARRAY[
  ROW('2008-09-09 10:00.00', st_makepoint(498919, 7247099)::POINT, 2, NULL,
      ARRAY[]::reittipiste_tehtava[],
      ARRAY[]::reittipiste_materiaali[])::reittipistedata,
  ROW('2008-09-09 10:03.00', st_makepoint(499271, 7248395) ::POINT, 2, NULL,
      ARRAY[]::reittipiste_tehtava[],
      ARRAY[(1, 2), (7,4)]::reittipiste_materiaali[])::reittipistedata,
  ROW('2008-09-09 10:06.00', st_makepoint(499399, 7249019) ::POINT, 2, NULL,
      ARRAY[]::reittipiste_tehtava[],
      ARRAY[(1, 8)]::reittipiste_materiaali[])::reittipistedata,
  ROW('2008-09-09 10:09.00', st_makepoint(440919, 7207099) ::POINT, 2, NULL,
      ARRAY[]::reittipiste_tehtava[],
      ARRAY[]::reittipiste_materiaali[])::reittipistedata
 ]::reittipistedata[]);


INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet) VALUES (
 (SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 1'),
 ARRAY[
  ROW('2005-11-11 10:03.51', st_makepoint(440271, 7208395) ::POINT, 2, NULL,
      ARRAY[]::reittipiste_tehtava[],
      ARRAY[]::reittipiste_materiaali[])::reittipistedata,
  ROW('2005-11-11 10:03.51', st_makepoint(440271, 7208395) ::POINT, 2, NULL,
      ARRAY[]::reittipiste_tehtava[],
      ARRAY[]::reittipiste_materiaali[])::reittipistedata,
  ROW('2005-11-11 10:06.00', st_makepoint(440399, 7209019) ::POINT, 2, NULL,
      ARRAY[]::reittipiste_tehtava[],
      ARRAY[]::reittipiste_materiaali[])::reittipistedata,
  ROW('2005-11-11 10:09.00',st_makepoint(440820, 7209885) ::POINT, 2, NULL,
      ARRAY[]::reittipiste_tehtava[],
      ARRAY[]::reittipiste_materiaali[])::reittipistedata
 ]::reittipistedata[]);

INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet) VALUES (
 (SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 2'),
 ARRAY[
   ROW('2005-11-11 10:01.00', st_makepoint(440119, 7207499) ::POINT, 2, NULL,
       ARRAY[]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata,
   ROW('2005-11-11 10:03.51', st_makepoint(440671, 7208695) ::POINT, 2, NULL,
       ARRAY[]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata,
   ROW('2005-11-11 10:06.00', st_makepoint(440399, 7209119) ::POINT, 2, NULL,
       ARRAY[]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata
 ]::reittipistedata[]);


INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet) VALUES (
 (SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 3'),
 ARRAY[
   ROW('2005-11-11 10:01.00', st_makepoint(440119, 7207199) ::POINT, 2, NULL,
       ARRAY[]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata,
   ROW('2005-11-11 10:03.51', st_makepoint(440171, 7208195) ::POINT, 2, NULL,
       ARRAY[]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata,
   ROW('2005-11-11 10:06.00', st_makepoint(440899, 7209119) ::POINT, 2, NULL,
       ARRAY[]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata,
   ROW('2005-11-11 10:09.00', st_makepoint(440520, 7209485) ::POINT, 2, NULL,
       ARRAY[]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata
 ]::reittipistedata[]);


INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet) VALUES (
 (SELECT id FROM toteuma WHERE lisatieto = 'Kokonaishintainen toteuma 4'),
 ARRAY[
   ROW('2005-11-11 10:01.00',st_makepoint(441119, 7206199) ::POINT, 2, NULL,
       ARRAY[]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata,
   ROW('2005-11-11 10:03.51', st_makepoint(441171, 7206195) ::POINT, 2, NULL,
       ARRAY[]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata,
   ROW('2005-11-11 10:06.00', st_makepoint(441899, 7207119) ::POINT, 2, NULL,
       ARRAY[]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata,
   ROW('2005-11-11 10:09.00', st_makepoint(441520, 7208485) ::POINT, 2, NULL,
       ARRAY[]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata
 ]::reittipistedata[]);



INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet) VALUES (
 (SELECT id FROM toteuma WHERE lisatieto = 'Pitkä kokonaishintainen reitti'),
 ARRAY[
   ROW('2015-02-01 17:01:00.000000', st_makepoint(430595.76308986434,7198842.856960223) ::POINT, 2, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:02:00.000000', st_makepoint(430672.2001181495,7199683.66427136) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:03:00.000000', st_makepoint(430748.6371464347,7200524.4715824975) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:04:00.000000', st_makepoint(430977.9482312902,7201441.715921919) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:05:00.000000', st_makepoint(430977.9482312902,7202129.649176486) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:06:00.000000', st_makepoint(430825.07417471986,7202894.019459338) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:07:00.000000', st_makepoint(430060.7038918681,7204652.0711098965) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:08:00.000000', st_makepoint(429907.8298352977,7205569.315449319) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:09:00.000000', st_makepoint(429678.5187504422,7206562.996817026) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:10:00.000000', st_makepoint(429525.64469387184,7207403.804128163) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:11:00.000000', st_makepoint(429525.64469387184,7207785.989269589) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:12:00.000000', st_makepoint(429525.64469387184,7208703.233609011) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:13:00.000000', st_makepoint(429602.081722157,7209544.040920148) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:14:00.000000', st_makepoint(429678.5187504422,7210690.596344425) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:15:00.000000', st_makepoint(429754.95577872737,7211760.714740418) ::POINT, 7, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:16:00.000000', st_makepoint(428531.9633261646,7213442.329362692) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:17:00.000000', st_makepoint(427996.90412816836,7213977.388560688) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:18:00.000000', st_makepoint(427614.71898674243,7214588.88478697) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:19:00.000000', st_makepoint(427156.0968170314,7215276.818041536) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:20:00.000000', st_makepoint(426850.3487038907,7215964.751296103) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
   ROW('2015-02-01 17:21:00.000000', st_makepoint(426773.9116756055,7216270.499409243) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[])
 ]::reittipistedata[]);

INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet) VALUES (
  (SELECT id FROM toteuma WHERE lisatieto = 'Pitkä kokonaishintainen reitti 2'),
  ARRAY[
    ROW('2015-02-01 17:01:00.000000', st_makepoint(430595.76308986434,7198842.856960223) ::POINT, 2, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:02:00.000000', st_makepoint(430672.2001181495,7199683.66427136) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:03:00.000000', st_makepoint(430748.6371464347,7200524.4715824975) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:04:00.000000', st_makepoint(430977.9482312902,7201441.715921919) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:05:00.000000', st_makepoint(430977.9482312902,7202129.649176486) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:06:00.000000', st_makepoint(430825.07417471986,7202894.019459338) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:07:00.000000', st_makepoint(430060.7038918681,7204652.0711098965) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:08:00.000000', st_makepoint(429907.8298352977,7205569.315449319) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:09:00.000000', st_makepoint(429678.5187504422,7206562.996817026) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:10:00.000000', st_makepoint(429525.64469387184,7207403.804128163) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:11:00.000000', st_makepoint(429525.64469387184,7207785.989269589) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:12:00.000000', st_makepoint(429525.64469387184,7208703.233609011) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:13:00.000000', st_makepoint(429602.081722157,7209544.040920148) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:14:00.000000', st_makepoint(429678.5187504422,7210690.596344425) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:15:00.000000', st_makepoint(429754.95577872737,7211760.714740418) ::POINT, 7, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:16:00.000000', st_makepoint(428531.9633261646,7213442.329362692) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:17:00.000000', st_makepoint(427996.90412816836,7213977.388560688) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:18:00.000000', st_makepoint(427614.71898674243,7214588.88478697) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:19:00.000000', st_makepoint(427156.0968170314,7215276.818041536) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:20:00.000000', st_makepoint(426850.3487038907,7215964.751296103) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[]),
    ROW('2015-02-01 17:21:00.000000', st_makepoint(426773.9116756055,7216270.499409243) ::POINT, 1, null, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[])
  ]::reittipistedata[]);


-- Reittipisteet muutostyölle
-- Tämä paikka on suunnilleen Muhoksella en tarkastanut kartalta kovin tarkasti..

INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet) VALUES (
 (SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö1'),
 ARRAY[
    ROW('2005-11-13 00:03.00',         st_makepoint(453271, 7188395) ::POINT, 2, NULL,
        ARRAY[ROW(1350, 10)::reittipiste_tehtava]::reittipiste_tehtava[],
        ARRAY[]::reittipiste_materiaali[])::reittipistedata,
    ROW('2005-11-13 00:06.00', st_makepoint(453399, 7189019) ::POINT, 2, NULL,
        ARRAY[ROW(1350,10)::reittipiste_tehtava]::reittipiste_tehtava[],
	ARRAY[]::reittipiste_materiaali[])::reittipistedata,
    ROW('2005-11-13 00:09.00', st_makepoint(453820, 7189885) ::POINT, 2, NULL,
        ARRAY[ROW(1350,10)]::reittipiste_tehtava[],
	ARRAY[]::reittipiste_materiaali[])::reittipistedata
 ]::reittipistedata[]);


-- Toinen muutoshintainen työ

INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet) VALUES (
 (SELECT id FROM toteuma WHERE lisatieto = 'Muutostyö2'),
 ARRAY[
   ROW('2005-11-13 10:00.00', st_makepoint(440919, 7207099) ::POINT, 2, NULL,
       ARRAY[ROW(1350,10)::reittipiste_tehtava]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata,
   ROW('2005-11-13 10:03.00',st_makepoint(440271, 7208395) ::POINT, 2, NULL,
       ARRAY[ROW(1350,10)::reittipiste_tehtava]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata,
   ROW('2005-11-13 10:06.00',st_makepoint(440399, 7209019) ::POINT, 2, NULL,
       ARRAY[ROW(1350,10)::reittipiste_tehtava]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata,
   ROW('2005-11-13 10:09.00', st_makepoint(440820, 7209885) ::POINT, 2, NULL,
       ARRAY[ROW(1350,10)::reittipiste_tehtava]::reittipiste_tehtava[],
       ARRAY[]::reittipiste_materiaali[])::reittipistedata
 ]::reittipistedata[]);


-- Reittipisteet yksikköhintaiselle työlle

INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet) VALUES (
  (SELECT id FROM toteuma WHERE lisatieto = 'Reitillinen yksikköhintainen toteuma 1'),
  ARRAY[
    ROW('2005-10-10 10:00.00', st_makepoint(498919, 7247099) ::POINT, 3, NULL, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[])::reittipistedata,
    ROW('2005-10-10 10:01.00',st_makepoint(499271, 7248395) ::POINT, 3, NULL, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[])::reittipistedata,
    ROW('2005-10-10 10:02.00',st_makepoint(499399, 7249019) ::POINT, 3, NULL, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[])::reittipistedata,
    ROW('2005-10-10 10:03.00',st_makepoint(499820, 7249885) ::POINT, 3, NULL, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[])::reittipistedata,
    ROW('2005-10-10 10:04.00',st_makepoint(498519, 7247299) ::POINT, 3, NULL, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[])::reittipistedata,
    ROW('2005-10-10 10:05.00',st_makepoint(499371, 7248595) ::POINT, 3, NULL, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[])::reittipistedata,
    ROW('2005-10-10 10:06.00',st_makepoint(499499, 7249319) ::POINT, 3, NULL, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[])::reittipistedata,
    ROW('2005-10-10 10:07.00',st_makepoint(499520, 7249685) ::POINT, 3, NULL, ARRAY[]::reittipiste_tehtava[], ARRAY[]::reittipiste_materiaali[])::reittipistedata
  ]::reittipistedata[]);


-- Suolatoteumia aktiiviselle Oulun urakalle

INSERT INTO toteuma
(lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, lisatieto, suorittajan_ytunnus, suorittajan_nimi, luoja)
VALUES
('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'),
(SELECT id FROM sopimus WHERE nimi = 'Aktiivinen Oulu Testi pääsopimus'),
NOW(),
NOW() - interval '1 hour',
NOW() - interval '55 minute',
'yksikkohintainen'::toteumatyyppi,
'Tämä on käsin tekaistu juttu Aktiiviselle Oulun urakalle',
'9184629-5',
'Antti Aurakuski',
(SELECT id FROM kayttaja WHERE kayttajanimi='jvh'));

INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet) VALUES (
  (SELECT id FROM toteuma WHERE lisatieto = 'Tämä on käsin tekaistu juttu Aktiiviselle Oulun urakalle'),
  ARRAY[
    ROW(NOW() - interval '59 minute',st_makepoint(430414, 7198924) ::POINT, 3, NULL, ARRAY[]::reittipiste_tehtava[],
        ARRAY[ROW(1, 1)::reittipiste_materiaali]::reittipiste_materiaali[])::reittipistedata,
    ROW(NOW() - interval '58 minute',st_makepoint(430702, 7198785) ::POINT, 3, NULL, ARRAY[]::reittipiste_tehtava[],
        ARRAY[ROW(2, 2)::reittipiste_materiaali]::reittipiste_materiaali[])::reittipistedata,
    ROW(NOW() - interval '57 minute',st_makepoint(430929, 7198500) ::POINT, 3, NULL, ARRAY[]::reittipiste_tehtava[],
        ARRAY[ROW(2, 2)::reittipiste_materiaali]::reittipiste_materiaali[])::reittipistedata,
    ROW(NOW() - interval '56 minute',st_makepoint(430969, 7198125) ::POINT, 3, NULL, ARRAY[]::reittipiste_tehtava[],
        ARRAY[ROW(3, 3)::reittipiste_materiaali]::reittipiste_materiaali[])::reittipistedata
  ]::reittipistedata[]);


-- partitioiden testausta varten luodaan toteumia
DO
$$
    DECLARE
        urakat INT[] := (SELECT array_agg(id) FROM urakka where tyyppi IN ('hoito', 'teiden-hoito'));
        urakkaid INT;
        aikaleima DATE;
        lisatieto_str TEXT;

    BEGIN
        RAISE NOTICE 'Aloitetaan toteumien generointi partitioita varten...';
        for counter in 1..1000 loop
                urakkaid := urakat[1+random()*(array_length(urakat, 1)-1)];
                aikaleima := (SELECT NOW() - '1 months'::INTERVAL * RANDOM() * 20 * RANDOM() * 2);
                lisatieto_str := 'rdm' || counter;

                -- reitillinen random toteuma
                INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto, luoja) VALUES ('harja-ui'::lahde, urakkaid, (SELECT id FROM sopimus WHERE urakka = urakkaid AND paasopimus IS null), NOW(), aikaleima, aikaleima + '1 minute'::interval, 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', lisatieto_str, (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));

                -- reitillisen random toteuman toteuma_tehtava
                INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id) VALUES ((SELECT id FROM toteuma WHERE lisatieto = lisatieto_str AND urakka = urakkaid), NOW(), 1369, 8, urakkaid);

                -- reitillisen random toteuman toteuman_reittipisteet

                INSERT INTO toteuman_reittipisteet (toteuma, reittipisteet)
                VALUES (
                           (SELECT id FROM toteuma WHERE lisatieto = lisatieto_str),
                           ARRAY[
                               ROW(aikaleima + '2 seconds'::interval, st_makepoint(498919, 7247099) ::POINT, 3, NULL, ARRAY[ROW(1369,1)::reittipiste_tehtava]::reittipiste_tehtava[], ARRAY[(1, 8)]::reittipiste_materiaali[])::reittipistedata,
                               ROW(aikaleima + '3 seconds'::interval,st_makepoint(499271, 7248395) ::POINT, 3, NULL, ARRAY[ROW(1369,1)::reittipiste_tehtava]::reittipiste_tehtava[], ARRAY[(1, 8)]::reittipiste_materiaali[])::reittipistedata,
                               ROW(aikaleima + '4 seconds'::interval,st_makepoint(499399, 7249019) ::POINT, 3, NULL, ARRAY[ROW(1369,1)::reittipiste_tehtava]::reittipiste_tehtava[], ARRAY[(1, 8)]::reittipiste_materiaali[])::reittipistedata,
                               ROW(aikaleima + '5 seconds'::interval,st_makepoint(499820, 7249885) ::POINT, 3, NULL, ARRAY[ROW(1369,1)::reittipiste_tehtava]::reittipiste_tehtava[], ARRAY[(1, 8)]::reittipiste_materiaali[])::reittipistedata,
                               ROW(aikaleima + '6 seconds'::interval,st_makepoint(498519, 7247299) ::POINT, 3, NULL, ARRAY[ROW(1369,1)::reittipiste_tehtava]::reittipiste_tehtava[], ARRAY[(1, 8)]::reittipiste_materiaali[])::reittipistedata,
                               ROW(aikaleima + '7 seconds'::interval,st_makepoint(499371, 7248595) ::POINT, 3, NULL, ARRAY[ROW(1369,1)::reittipiste_tehtava]::reittipiste_tehtava[], ARRAY[(1, 8)]::reittipiste_materiaali[])::reittipistedata,
                               ROW(aikaleima + '8 seconds'::interval,st_makepoint(499499, 7249319) ::POINT, 3, NULL, ARRAY[ROW(1369,1)::reittipiste_tehtava]::reittipiste_tehtava[], ARRAY[(1, 8)]::reittipiste_materiaali[])::reittipistedata,
                               ROW(aikaleima + '9 seconds'::interval,st_makepoint(499520, 7249685) ::POINT, 3, NULL, ARRAY[ROW(1369,1)::reittipiste_tehtava]::reittipiste_tehtava[], ARRAY[(1, 8)]::reittipiste_materiaali[])::reittipistedata
                               ]::reittipistedata[]);
            END LOOP;
        RAISE NOTICE 'Toteumien generointi partitioita varten valmis.';
    END
$$ LANGUAGE plpgsql;

-- Luodaan vielä tulevaisuuden partitioille testidataa jotta voidaan varmistaa trigger-funktion ohjaaminen
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto, luoja) VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2020-09-01 00:12:00+02', '2020-09-01 00:15:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', '2020h2', (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero')),
('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2021-03-01 00:12:00+02', '2021-03-01 00:15:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', '2021h1', (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero')),
('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2021-09-01 00:12:00+02', '2021-09-01 00:15:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', '2021h2', (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero')),
('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2022-03-01 00:12:00+02', '2022-03-01 00:15:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', '2022h1', (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero')),
('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2022-09-01 00:12:00+02', '2022-09-01 00:15:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', '2022h2', (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero')),
('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2023-03-01 00:12:00+02', '2023-03-01 00:15:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', '2023h1', (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero')),
('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2023-09-01 00:12:00+02', '2023-09-01 00:15:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', '2023h2', (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero')),
('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2024-03-01 00:12:00+02', '2024-03-01 00:15:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', '2024h1', (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero')),
('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2024-09-01 00:12:00+02', '2024-09-01 00:15:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', '2024h2', (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero')),
('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi') AND paasopimus IS null), NOW(), '2025-03-01 00:12:00+02', '2025-03-01 00:15:00+02', 'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', '2025h1', (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero'));


-- Päivitetään kaikille toteumille reitti reittipisteiden mukaan.
-- Oikeissa toteumissa nämä tulevat projisoituna tieverkolle, mutta nyt vain tehdään viivat.
UPDATE toteuma t
   SET reitti = (SELECT ST_MakeLine(p.sij)
                   FROM (SELECT rp.sijainti::geometry as sij
                           FROM toteuman_reittipisteet tr
                                    LEFT JOIN LATERAL unnest(tr.reittipisteet) AS rp ON TRUE
                          WHERE tr.toteuma = t.id
                          ORDER BY rp.aika) p)
 WHERE reitti IS NULL;

-- Varmistetaan, että kaikilla toteumilla on käyttäjä
UPDATE toteuma SET luoja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'destia') WHERE luoja IS NULL;
UPDATE toteuma_tehtava SET luoja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'destia') WHERE luoja IS NULL;

-- Määritellään vain halutut tehtävät mahdolliseksi lisätä suunnitellulle tehtävälle käsin
UPDATE toimenpidekoodi set kasin_lisattava_maara = TRUE WHERE ID in (1428,1431,1430,1429,3062,1439,1403,3021,3022, 3025, 3026, 3023, 3024,3027, 3028, 3063,3064, 3065, 3066, 3067, 1414,3040, 3068, 3041, 3042, 3043, 3069,3050, 3051);