-- Tämä tiedosto sisältää Espoon alueurakka 2014-2019 testidataa, jonka avulla voidaan luoda mielekäs laskutusyhteenveto
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus)
     VALUES (2014, 10, 3500, '2014-10-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2014, 11, 3500, '2014-11-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2014, 12, 3500, '2014-12-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 1, 3500, '2015-01-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 2, 3500, '2015-02-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 3, 3500, '2015-03-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 4, 3500, '2015-04-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 5, 3500, '2015-05-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 6, 3500, '2015-06-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 7, 3500, '2015-07-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 8, 3500, '2015-08-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 9, 3500, '2015-09-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2014, 10, 10000, '2014-10-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2014, 11, 10000, '2014-11-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2014, 12, 10000, '2014-12-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 1, 10000, '2015-01-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 2, 10000, '2015-02-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 3, 10000, '2015-03-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 4, 10000, '2015-04-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 5, 10000, '2015-05-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 6, 10000, '2015-06-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 7, 10000, '2015-07-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            (2015, 8, 10000, '2015-08-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
            -- Lisäsopimuksesta kokonaishintaista työtä mukaan myös
            (2015, 9, 10, '2015-09-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS NOT null)),
            (2015, 9, 10000, '2015-09-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null));

INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus)
VALUES (2015, 10, 3500, '2015-10-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2015, 11, 3500, '2015-11-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2015, 12, 3500, '2015-12-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 1, 3500, '2016-01-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 2, 3500, '2016-02-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 3, 3500, '2016-03-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 4, 3500, '2016-04-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 5, 3500, '2016-05-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 6, 3500, '2016-06-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 7, 3500, '2016-07-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 8, 3500, '2016-08-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 9, 3500, '2016-09-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2015, 10, 10000, '2015-10-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2015, 11, 10000, '2015-11-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2015, 12, 10000, '2015-12-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 1, 10000, '2016-01-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 2, 10000, '2016-02-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 3, 10000, '2016-03-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 4, 10000, '2016-04-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 5, 10000, '2016-05-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 6, 10000, '2016-06-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 7, 10000, '2016-07-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  (2016, 8, 10000, '2016-08-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
  -- Lisäsopimuksesta kokonaishintaista työtä mukaan myös
  (2016, 9, 10, '2016-09-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS NOT null)),
  (2016, 9, 10000, '2016-09-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Sorateiden hoito TP 2014-2019'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus)
    VALUES ('2014-10-01', '2014-12-31', 3, 'km', 100, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Is 2-ajorat. KVL >15000'), (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
           ('2015-01-01', '2015-09-30', 9, 'km', 100, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Is 2-ajorat. KVL >15000'), (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
           ('2014-10-01', '2014-12-31', 60, 'ha', 100, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Metsän harvennus'), (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
           ('2015-01-01', '2015-09-30', 180, 'ha', 100, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Metsän harvennus'), (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null));

INSERT INTO muutoshintainen_tyo (alkupvm, loppupvm, yksikko, yksikkohinta, tehtava, urakka, sopimus)
VALUES ((SELECT alkupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), 'tiekm', 100.0, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Is 1-ajorat. KVL >15000'), (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null)),
       ((SELECT alkupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), 'ha', 100.0, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Vesakonraivaus'), (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null));

-- Suolauksen toteuma (materiaalitoteuma)
INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
     VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
             (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
             '2015-02-19 10:23:54+02', '2015-02-18 00:00:00+02', '2015-02-18 02:00:00+02',
             'kokonaishintainen'::toteumatyyppi, 'Seppo Suorittaja', '4153724-6', 'ESP-LYV-toteuma');
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, urakka_id)
    VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'ESP-LYV-toteuma'), '2015-02-19 10:23:54+02',
            (SELECT id FROM materiaalikoodi WHERE nimi='Talvisuolaliuos NaCl'), 2000, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'));

INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
VALUES ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
       (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
        '2015-01-19 10:23:54+02', '2015-01-19 10:23:54+02', '2015-01-19 10:23:54+02', 'yksikkohintainen'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_yht_tot1'),
       ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
        (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
        '2015-01-19 10:23:54+02', '2015-01-19 10:23:54+02', '2015-01-19 10:23:54+02', 'yksikkohintainen'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_yht_tot2'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-07-19 10:23:54+02', '2015-07-19 10:23:54+02', '2015-07-19 10:23:54+02', 'yksikkohintainen'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_yht_tot_heinakuu'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-08-01 00:00:00+02', '2015-08-01 00:00:00+02', '2015-08-01 00:00:00+02', 'yksikkohintainen'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_yht_tot_elokuu_eka'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-08-19 10:23:54+02', '2015-08-19 10:23:54+02', '2015-08-19 10:23:54+02', 'yksikkohintainen'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_yht_tot_elokuu'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-08-20 10:23:54+02', '2015-08-20 10:23:54+02', '2015-08-20 10:23:54+02', 'yksikkohintainen'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_yht_tot_elokuu2'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-07-19 10:23:54+02', '2015-07-19 10:23:54+02', '2015-07-19 10:23:54+02', 'muutostyo'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_muutostyo_tot_heinakuu'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-07-12 10:23:54+02', '2015-07-12 10:23:54+02', '2015-07-12 10:23:54+02', 'muutostyo'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_muutostyo_tot_heinakuu_paivanhinta'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-08-01 00:00:00+02', '2015-08-01 00:00:00+02', '2015-08-01 00:00:00+02', 'muutostyo'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_muutostyo_tot_elokuu_eka'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-08-01 00:00:00+02', '2015-08-01 00:00:00+02', '2015-08-01 00:00:00+02', 'muutostyo'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_muutostyo_tot_elokuu_eka_paivanhinta'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-08-19 10:23:54+02', '2015-08-19 10:23:54+02', '2015-08-19 10:23:54+02', 'lisatyo'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_lisatyo_tot_elokuu'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-08-10 10:23:54+02', '2015-08-10 10:23:54+02', '2015-08-10 10:23:54+02', 'lisatyo'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_lisatyo_tot_elokuu_paivanhinta_1'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-08-20 10:23:54+02', '2015-08-20 10:23:54+02', '2015-08-20 10:23:54+02', 'lisatyo'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_lisatyo_tot_elokuu_paivanhinta_2'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-07-22 10:23:54+02', '2015-07-20 10:23:54+02', '2015-07-20 10:23:54+02', 'akillinen-hoitotyo'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_akillinen_tot_heinakuu'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-08-22 10:23:54+02', '2015-08-20 10:23:54+02', '2015-08-20 10:23:54+02', 'akillinen-hoitotyo'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_akillinen_tot_elokuu'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-08-23 10:23:54+02', '2015-08-20 10:23:54+02', '2015-08-20 10:23:54+02', 'akillinen-hoitotyo'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_akillinen_tot_elokuu_paivanhinta'),
  ('harja-ui'::lahde, (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
   (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null),
   '2015-08-24 10:23:54+02', '2015-08-20 10:23:54+02', '2015-08-20 10:23:54+02', 'vahinkojen-korjaukset'::toteumatyyppi, 'Antti Ahertaja', '1524792-1', 'ESPlyv_vahinkojen-korjaukset_tot_elokuu_paivanhinta');

INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, urakka_id)
VALUES
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_yht_tot1'), '2015-01-19 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Is 2-ajorat. KVL >15000'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_yht_tot1')),
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_yht_tot2'), '2015-01-19 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Metsän harvennus'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_yht_tot2')),
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_yht_tot_heinakuu'), '2015-07-19 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Is 2-ajorat. KVL >15000'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_yht_tot_heinakuu')),
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_yht_tot_elokuu_eka'), '2015-08-02 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Metsän harvennus'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_yht_tot_elokuu_eka')),
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_yht_tot_elokuu'), '2015-08-19 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Metsän harvennus'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_yht_tot_elokuu')),
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_yht_tot_elokuu2'), '2015-08-20 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Metsän harvennus'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_yht_tot_elokuu2')),
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_muutostyo_tot_heinakuu'), '2015-07-19 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Is 1-ajorat. KVL >15000'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_muutostyo_tot_heinakuu')),
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_muutostyo_tot_heinakuu'), '2015-07-19 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Vesakonraivaus'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_muutostyo_tot_heinakuu')),
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_muutostyo_tot_elokuu_eka'), '2015-08-19 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Is 1-ajorat. KVL >15000'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_muutostyo_tot_elokuu_eka')),
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_lisatyo_tot_elokuu'), '2015-08-19 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Vesakonraivaus'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_lisatyo_tot_elokuu')),
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_akillinen_tot_heinakuu'), '2015-07-19 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Vesakonraivaus'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_akillinen_tot_heinakuu')),
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_akillinen_tot_elokuu'), '2015-08-19 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Vesakonraivaus'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_akillinen_tot_elokuu')),
  ((SELECT id from toteuma where lisatieto = 'ESPlyv_vahinkojen-korjaukset_tot_elokuu_paivanhinta'), '2015-08-19 00:00.00', (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Vesakonraivaus'), 10, (SELECT urakka from toteuma where lisatieto = 'ESPlyv_vahinkojen-korjaukset_tot_elokuu_paivanhinta'));

INSERT INTO toteuma_tehtava (toteuma, toimenpidekoodi, maara, paivan_hinta, indeksi, urakka_id)
VALUES
  ((SELECT id FROM toteuma WHERE lisatieto = 'ESPlyv_muutostyo_tot_heinakuu_paivanhinta'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Metsän harvennus'), 10, 1000, false, (SELECT urakka FROM toteuma WHERE lisatieto = 'ESPlyv_muutostyo_tot_heinakuu_paivanhinta')),
  ((SELECT id FROM toteuma WHERE lisatieto = 'ESPlyv_muutostyo_tot_heinakuu_paivanhinta'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Vesakonraivaus'), 10, 1000, false, (SELECT urakka FROM toteuma WHERE lisatieto = 'ESPlyv_muutostyo_tot_heinakuu_paivanhinta')),
  ((SELECT id FROM toteuma WHERE lisatieto = 'ESPlyv_muutostyo_tot_elokuu_eka_paivanhinta'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Metsän harvennus'), 10, 1000, false, (SELECT urakka FROM toteuma WHERE lisatieto = 'ESPlyv_muutostyo_tot_elokuu_eka_paivanhinta')),
  ((SELECT id FROM toteuma WHERE lisatieto = 'ESPlyv_muutostyo_tot_elokuu_eka_paivanhinta'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Vesakonraivaus'), 10, 1000, false, (SELECT urakka FROM toteuma WHERE lisatieto = 'ESPlyv_muutostyo_tot_elokuu_eka_paivanhinta')),
  ((SELECT id FROM toteuma WHERE lisatieto = 'ESPlyv_lisatyo_tot_elokuu_paivanhinta_1'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Metsän harvennus'), 10, 1000, false, (SELECT urakka FROM toteuma WHERE lisatieto = 'ESPlyv_lisatyo_tot_elokuu_paivanhinta_1')),
  ((SELECT id FROM toteuma WHERE lisatieto = 'ESPlyv_lisatyo_tot_elokuu_paivanhinta_1'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Vesakonraivaus'), 10, 1000, false, (SELECT urakka FROM toteuma WHERE lisatieto = 'ESPlyv_lisatyo_tot_elokuu_paivanhinta_1')),
  ((SELECT id FROM toteuma WHERE lisatieto = 'ESPlyv_lisatyo_tot_elokuu_paivanhinta_2'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Metsän harvennus'), 10, 1000, false, (SELECT urakka FROM toteuma WHERE lisatieto = 'ESPlyv_lisatyo_tot_elokuu_paivanhinta_2')),
  ((SELECT id FROM toteuma WHERE lisatieto = 'ESPlyv_lisatyo_tot_elokuu_paivanhinta_2'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Vesakonraivaus'), 10, 1000, false, (SELECT urakka FROM toteuma WHERE lisatieto = 'ESPlyv_lisatyo_tot_elokuu_paivanhinta_2')),
  ((SELECT id FROM toteuma WHERE lisatieto = 'ESPlyv_akillinen_tot_elokuu_paivanhinta'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Vesakonraivaus'), 10, 1000, false, (SELECT urakka FROM toteuma WHERE lisatieto = 'ESPlyv_akillinen_tot_elokuu_paivanhinta'));

--Erilliskustannukset
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja)
VALUES
  ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), '2015-05-15', -1000, 'MAKU 2010', 'Urakoitsija maksaa tilaajalle', '2015-09-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh')),
  ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), '2015-06-15', 1000, 'MAKU 2010', 'Vahingot on nyt korjattu, lasku tulossa.', '2015-09-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh')),
  ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), '2015-07-15', 1000, null, 'Tilaaja maksaa urakoitsijlle.', '2015-09-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh')),
  ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), '2015-08-01', 1000, 'MAKU 2010', 'Muu erilliskustannus', '2015-08-01', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh')),
  ('asiakastyytyvaisyysbonus', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), '2015-08-15', 1000, 'MAKU 2005', 'Asiakkaat erittäin tyytyväisiä, tyytyväisyysindeksi 0,92.', '2015-09-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh')),
  ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Espoo Talvihoito TP 2014-2019'), '2015-09-15', 1000, 'MAKU 2010', 'Muun erilliskustannuksen lisätieto', '2015-09-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));


-- Sydäntalven lämpötila hoitokaudella ja pitkän ajan keskiarvo, vaikuttaa sallittuun suolamäärään
INSERT INTO lampotilat (urakka, alkupvm, loppupvm, keskilampotila, pitka_keskilampotila)
     VALUES ((SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'),
            '2014-10-01', '2015-09-30', -6.0, -8.8);

-- Suolasakon suuruus ja sidottava indeksi
INSERT INTO suolasakko (maara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka, talvisuolaraja)
     VALUES (30.0, 2014, 8, 'MAKU 2005', (SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), 800);

-- Maksuerät
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019'), 'kokonaishintainen', 'Espoo Talvihoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019'), 'yksikkohintainen', 'Espoo Talvihoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019'), 'lisatyo', 'Espoo Talvihoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019'), 'indeksi', 'Espoo Talvihoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019'), 'bonus', 'Espoo Talvihoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019'), 'sakko', 'Espoo Talvihoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019'), 'akillinen-hoitotyo', 'Espoo Talvihoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019'), 'muu', 'Espoo Talvihoito TP ME 2014-2019' );

INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019'), 'kokonaishintainen', 'Espoo Liikenneympäristön hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019'), 'yksikkohintainen', 'Espoo Liikenneympäristön hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019'), 'lisatyo', 'Espoo Liikenneympäristön hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019'), 'indeksi', 'Espoo Liikenneympäristön hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019'), 'bonus', 'Espoo Liikenneympäristön hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019'), 'sakko', 'Espoo Liikenneympäristön hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019'), 'akillinen-hoitotyo', 'Espoo Liikenneympäristön hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019'), 'muu', 'Espoo Liikenneympäristön hoito TP ME 2014-2019' );

INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019'), 'kokonaishintainen', 'Espoo Sorateiden hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019'), 'yksikkohintainen', 'Espoo Sorateiden hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019'), 'lisatyo', 'Espoo Sorateiden hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019'), 'indeksi', 'Espoo Sorateiden hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019'), 'bonus', 'Espoo Sorateiden hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019'), 'sakko', 'Espoo Sorateiden hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019'), 'akillinen-hoitotyo', 'Espoo Sorateiden hoito TP ME 2014-2019' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES ((SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019'), 'muu', 'Espoo Sorateiden hoito TP ME 2014-2019' );

-- Kustannussuunnitelmat
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019') AND tyyppi = 'kokonaishintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019') AND tyyppi = 'yksikkohintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019') AND tyyppi = 'lisatyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019') AND tyyppi = 'indeksi'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019') AND tyyppi = 'bonus'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019') AND tyyppi = 'sakko'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019') AND tyyppi = 'akillinen-hoitotyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019') AND tyyppi = 'muu'));

INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019') AND tyyppi = 'kokonaishintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019') AND tyyppi = 'yksikkohintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019') AND tyyppi = 'lisatyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019') AND tyyppi = 'indeksi'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019') AND tyyppi = 'bonus'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019') AND tyyppi = 'sakko'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019') AND tyyppi = 'akillinen-hoitotyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019') AND tyyppi = 'muu'));

INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019') AND tyyppi = 'kokonaishintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019') AND tyyppi = 'yksikkohintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019') AND tyyppi = 'lisatyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019') AND tyyppi = 'indeksi'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019') AND tyyppi = 'bonus'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019') AND tyyppi = 'sakko'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019') AND tyyppi = 'akillinen-hoitotyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = (SELECT id from toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019') AND tyyppi = 'muu'));

-- Sanktioita
INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio, luoja)
VALUES ('A'::sanktiolaji, 100, '2015-01-12 06:06.37', 'MAKU 2010', null, (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Espoo Talvihoito TP 2014-2019'), 1, true, 2),
  ('A'::sanktiolaji, 500, '2015-05-12 06:06.37', 'MAKU 2010', null, (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019'), 1, true, 2),
  ('A'::sanktiolaji, 700, '2015-07-12 06:06.37', null, null, (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Espoo Liikenneympäristön hoito TP 2014-2019'), 1, true, 2),
  ('A'::sanktiolaji, 1000, '2015-08-01 00:00.00', 'MAKU 2010', null, (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019'), 1, true, 2),
  ('A'::sanktiolaji, 800, '2015-08-12 06:06.37', 'MAKU 2010', null, (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019'), 1, true, 2),
  ('A'::sanktiolaji, 900, '2015-09-12 06:06.37', 'MAKU 2010', null, (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019'), 1, true, 2),
  ('A'::sanktiolaji, 20160, '2016-09-12 06:06.37', 'MAKU 2010', null, (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Espoo Sorateiden hoito TP 2014-2019'), 1, true, 2);
