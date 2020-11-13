-- Testidataan luodaan olosuhteet kuten AURAssa urakassa Vantaa, hoitokausi 2015-2016 siltä osin kuin data vaikuttaa talvisuolan laskentaan
-- Indeksilaskennan perusluvuksi tultava 119.1 (ks. MAKU 2005 indeksien testidata vuosille 2008 ja 2009
-- Indeksit samat kuin AURAssa MAKU 2005 talvikauden 2015-2016 loka-maaliskuulle
-- käytetty talvisuola 824,60
-- talvisuolaraja 1100
-- Sydäntalven lämpötilat samat kuin AURAssa Vantaa, hoitokausi 2015-2016 (ks lämpötilojen testidata)
-- Lopputulos oltava Suolabonus 6072e, ja indeksi 850,56e

INSERT INTO lampotilat (urakka, alkupvm, loppupvm, keskilampotila, pitka_keskilampotila, pitka_keskilampotila_vanha)
VALUES ((SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'),
        '2015-10-01', '2016-09-30', -3.5, -5.6, -5.6);

-- Talvisuolaraja, suolabonuksen/suolasakon suuruus ja sidottava indeksi
INSERT INTO suolasakko (maara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka, talvisuolaraja)
VALUES (30.0, 2009, 8, 'MAKU 2005', (SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), 1100);

INSERT INTO suolasakko (maara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka, talvisuolaraja)
VALUES (30.0, 2015, 8, 'MAKU 2005', (SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), 1100);

-- Suolauksen toteuma (materiaalitoteuma)
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019') AND paasopimus IS null),
        '2016-02-19 10:23:54+02', '2016-02-18 00:00:00+02', '2016-02-18 02:00:00+02',
        'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '6153860-9', 'VAN-SS-toteuma');
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id)
VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'VAN-SS-toteuma'), '2016-02-19 10:23:54+02',
        (SELECT id FROM materiaalikoodi WHERE nimi='Talvisuolaliuos NaCl'), 842.6, (SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'));
