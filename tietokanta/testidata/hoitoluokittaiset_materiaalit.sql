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
VALUES ('11244001', 4, 2015, 6.6, 846), ('11244001', 4, 2016, 6.6, 846), ('11244001', 4, 2016, 6.6, 4);

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

-- Kesäsuola sorateiden pölynsidonta (materiaalitoteuma) Ouluun - Testataan soratiehoitoluokkia
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto, luoja)
VALUES ('harja-api'::lahde, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019') AND paasopimus IS null),
        NOW(), '2018-02-15 13:00:00+02', '2018-02-15 13:00:00+02',
        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'Pölynsidontaa Pikkaralassa',
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'));

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id, luoja)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Pölynsidontaa Pikkaralassa'), NOW(),
        (SELECT id FROM materiaalikoodi WHERE nimi = 'Kesäsuola sorateiden pölynsidonta'), 0.88,
        (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'));

INSERT INTO toteuman_reittipisteet (toteuma, luotu, reittipisteet) VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Pölynsidontaa Pikkaralassa'), NOW(),
                                                                           ARRAY[
                                                                               ROW('2018-02-15 10:00.00', st_makepoint(440816, 7198387)::POINT, NULL, 2,
                                                                                   ARRAY[(1361,0.25)]::reittipiste_tehtava[],
                                                                                   ARRAY[(11, 0.44)]::reittipiste_materiaali[])::reittipistedata,
                                                                               ROW('2018-02-15 10:03.00', st_makepoint(441271, 7198865) ::POINT, NULL, 2,
                                                                                   ARRAY[(1361,0.25)]::reittipiste_tehtava[],
                                                                                   ARRAY[(11, 0.44)]::reittipiste_materiaali[])::reittipistedata
                                                                               ]::reittipistedata[]);


-- Oulun alueurakka 2014-2019, talvisuolauksen GPS-pisteet kevyen liikenteen väylillä.
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto, luoja)
VALUES ('harja-api'::lahde, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019') AND paasopimus IS null),
        NOW(), '2018-10-15 13:00:00+02', '2018-10-15 13:03:00+02',
        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'Talvisuolaus Ylikiimingissä',
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'));

INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id, luoja)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Talvisuolaus Ylikiimingissä'), NOW(),
        (SELECT id FROM materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'), 3.14,
        (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'yit-rakennus'));

-- Reittipisteet kohdistuneet osittain kevyen liikenteen väylille
INSERT INTO toteuman_reittipisteet (toteuma, luotu, reittipisteet)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Talvisuolaus Ylikiimingissä'), NOW(),
        ARRAY [
            -- kelvi
            ROW ('2018-10-15 13:00.00', st_makepoint(460185.66, 7212072.31)::POINT, 10, NULL,
                ARRAY [((SELECT id FROM tehtava WHERE nimi = 'Suolaus'), 0.785)]::reittipiste_tehtava[],
                ARRAY [((SELECT id FROM materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'),
                        0.785)]::reittipiste_materiaali[])::reittipistedata,
            -- kelvi
            ROW ('2018-10-15 13:01.00', st_makepoint(460213.40, 7212061.69) ::POINT, 10, NULL,
                ARRAY [((SELECT id FROM tehtava WHERE nimi = 'Suolaus'), 0.785)]::reittipiste_tehtava[],
                ARRAY [((SELECT id FROM materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'),
                        0.785)]::reittipiste_materiaali[])::reittipistedata,
            -- ajoväylä
            ROW ('2018-10-15 13:02.00', st_makepoint(460242.71, 7212056.21) ::POINT, 6, NULL,
                ARRAY [((SELECT id FROM tehtava WHERE nimi = 'Suolaus'), 0.785)]::reittipiste_tehtava[],
                ARRAY [((SELECT id FROM materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'),
                        0.785)]::reittipiste_materiaali[])::reittipistedata,
            -- ajoväylä
            ROW ('2018-10-15 13:03.00', st_makepoint(460279.10, 7212042.28) ::POINT, 6, NULL,
                ARRAY [((SELECT id FROM tehtava WHERE nimi = 'Suolaus'), 0.785)]::reittipiste_tehtava[],
                ARRAY [((SELECT id FROM materiaalikoodi WHERE nimi = 'Talvisuola, rakeinen NaCl'),
                        0.785)]::reittipiste_materiaali[])::reittipistedata
            ]::reittipistedata[]);

-- Päivitä pohjavesialueen talvisuolakooste
SELECT paivita_pohjavesialue_kooste();
