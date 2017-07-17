-- ***********************************************
-- VÄYLÄT
-- ***********************************************

INSERT INTO vv_vayla
(nimi, tyyppi, vaylanro)
VALUES
  ('Hietasaaren läntinen rinnakkaisväylä', 'kauppamerenkulku' :: VV_VAYLATYYPPI, 66661);

INSERT INTO vv_vayla
(nimi, tyyppi, vaylanro)
VALUES
  ('Oulaisten meriväylä', 'muu' :: VV_VAYLATYYPPI, 66662);


INSERT INTO vv_vayla
(nimi, tyyppi, vaylanro)
VALUES
  ('Akonniemen väylät', 'kauppamerenkulku' :: VV_VAYLATYYPPI, 66663);

INSERT INTO vv_vayla
(nimi, tyyppi, vaylanro)
VALUES
  ('Muu väylä', 'muu' :: VV_VAYLATYYPPI, 66664);

-- ***********************************************
-- TURVALAITTEET
-- ***********************************************

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vaylat, turvalaitenro)
VALUES
('Hietasaaren pienempi poiju', 'xx-1234', 'poiju', '{66662}', '1234');

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vaylat, turvalaitenro)
VALUES
('Hietasaaren poiju', 'xx-12345', 'poiju', '{66662}', '12345');

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vaylat, turvalaitenro)
VALUES
('Hietasaaren viitta', 'xx-12346', 'viitta', '{66662}', '12346');

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vaylat, kiintea, turvalaitenro)
VALUES
('Hietasaaren kyltti', 'xx-12347', 'tuntematon', '{66662}', TRUE, '12347');

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vaylat, kiintea, turvalaitenro)
VALUES
('Akonniemen kyltti', 'xx-123', 'tuntematon', '{66663}', TRUE, '123');

-- testitoimenpiteissä käytetyt turvalaitenro-viittaukset
INSERT INTO vv_turvalaite (tunniste, turvalaitenro, tyyppi, nimi, vaylat) VALUES ('xx-8881',8881, 'poiju', 'poiju 1', '{66661}');
INSERT INTO vv_turvalaite (tunniste, turvalaitenro,  tyyppi, nimi, vaylat) VALUES ('xx-8882',8882, 'poiju', 'poiju 2', '{66661}');
INSERT INTO vv_turvalaite (tunniste, turvalaitenro, tyyppi, nimi, vaylat) VALUES ('xx-8884',8884, 'poiju', 'poiju 4', '{66661}');
INSERT INTO vv_turvalaite (tunniste, turvalaitenro,  tyyppi, nimi, vaylat) VALUES ('xx-8890',8890, 'poiju', 'poiju 10', '{66661}');
INSERT INTO vv_turvalaite (tunniste, turvalaitenro,  tyyppi, nimi, vaylat) VALUES ('xx-8891', 8891,'poiju', 'poiju 11', '{66661}');
INSERT INTO vv_turvalaite (tunniste, turvalaitenro,  tyyppi, nimi, vaylat) VALUES ('xx-666', 666, 'poiju', 'poiju 666', '{66661}');

-- ***********************************************
-- TURVALAITEKOMPONENTTITYYPIT
-- ***********************************************

INSERT INTO reimari_komponenttityyppi (id, nimi, lisatiedot, "luokan-id", "luokan-nimi", "luokan-lisatiedot", "luokan-paivitysaika", "luokan-luontiaika", "merk-cod", paivitysaika, luontiaika, muokattu, alkupvm, loppupvm)
VALUES ('1', 'Lateraalimerkki', 'PV 800 oikea', '1200', 'Viitat/Lateriaalimerkit', 'Ihan kiva luokka', '2003-06-09',
             '2003-06-09 09:00:00', '2003-06-09 09:00:00	0', '2012-10-02', '2003-06-09 10:02:32.462',
        '2012-10-02 10:02:32.462', '2012-10-02 00:00:00', '2099-12-31 00:00:00');

INSERT INTO reimari_komponenttityyppi (id, nimi, lisatiedot, "luokan-id", "luokan-nimi", "luokan-lisatiedot", "luokan-paivitysaika", "luokan-luontiaika", "merk-cod", paivitysaika, luontiaika, muokattu, alkupvm, loppupvm)
VALUES ('2', 'Lateraalimerkki', 'PV 800 vasen', '1200', 'Viitat/Lateriaalimerkit', 'Ihan kiva luokka', '2003-06-09',
             '2003-06-09 09:00:00', '2003-06-09 09:00:00	0', '2012-10-02', '2003-06-09 10:02:32.462',
        '2012-10-02 10:02:32.462', '2012-10-02 00:00:00', '2099-12-31 00:00:00');

INSERT INTO reimari_komponenttityyppi (id, nimi, lisatiedot, "luokan-id", "luokan-nimi", "luokan-lisatiedot", "luokan-paivitysaika", "luokan-luontiaika", "merk-cod", paivitysaika, luontiaika, muokattu, alkupvm, loppupvm)
VALUES ('3', 'Lateraalimerkki', 'PV 800 Etelä', '1200', 'Viitat/Lateriaalimerkit', 'Ihan kiva luokka', '2003-06-09',
             '2003-06-09 09:00:00', '2003-06-09 09:00:00	0', '2012-10-02', '2003-06-09 10:02:32.462',
        '2012-10-02 10:02:32.462', '2012-10-02 00:00:00', '2099-12-31 00:00:00');

INSERT INTO reimari_komponenttityyppi (id, nimi, lisatiedot, "luokan-id", "luokan-nimi", "luokan-lisatiedot", "luokan-paivitysaika", "luokan-luontiaika", "merk-cod", paivitysaika, luontiaika, muokattu, alkupvm, loppupvm)
VALUES ('4', 'Lateraalimerkki', 'PV 800 Pohjoinen', '1200', 'Viitat/Lateriaalimerkit', 'Ihan kiva luokka', '2003-06-09',
             '2003-06-09 09:00:00', '2003-06-09 09:00:00	0', '2012-10-02', '2003-06-09 10:02:32.462',
        '2012-10-02 10:02:32.462', '2012-10-02 00:00:00', '2099-12-31 00:00:00');

-- ***********************************************
-- TURVALAITEKOMPONENTIT
-- ***********************************************

INSERT INTO reimari_turvalaitekomponentti (id, lisatiedot, turvalaitenro, "komponentti-id", sarjanumero, paivitysaika, luontiaika, luoja, muokkaaja, muokattu, alkupvm, valiaikainen, loppupvm)
VALUES ('-2139967596', 'Hieno komponentti', '1', '1', '123', '2016-02-18 10:39:14.39', '2017-05-31', 'MERITAITO',
                       'MERITAITO', '2016-02-18 10:39:14.39', '2017-01-01', FALSE, '2017-12-12');

INSERT INTO reimari_turvalaitekomponentti (id, lisatiedot, turvalaitenro, "komponentti-id", sarjanumero, paivitysaika, luontiaika, luoja, muokkaaja, muokattu, alkupvm, valiaikainen, loppupvm)
VALUES ('-567765567', 'Hieno komponentti on', '2', '2', '123', '2016-02-18 10:39:14.39', '2017-05-31', 'MERITAITO',
                      'MERITAITO', '2016-02-18 10:39:14.39', '2017-01-01', FALSE, '2017-12-12');

INSERT INTO reimari_turvalaitekomponentti (id, lisatiedot, turvalaitenro, "komponentti-id", sarjanumero, paivitysaika, luontiaika, luoja, muokkaaja, muokattu, alkupvm, valiaikainen, loppupvm)
VALUES
  ('-4556365653', 'Hieno komponentti juu', '3', '3', '123', '2016-02-18 10:39:14.39', '2017-05-31', 'PASISA', 'PASISA',
                  '2016-02-18 10:39:14.39', '2017-01-01', FALSE, '2017-12-12');

INSERT INTO reimari_turvalaitekomponentti (id, lisatiedot, turvalaitenro, "komponentti-id", sarjanumero, paivitysaika, luontiaika, luoja, muokkaaja, muokattu, alkupvm, valiaikainen, loppupvm)
VALUES ('-6785597846', 'Hieno komponentti se on', '4', '4', '123', '2016-02-18 10:39:14.39', '2017-05-31', 'PASISA',
                       'PASISA', '2016-02-18 10:39:14.39', '2017-01-01', FALSE, '2017-12-12');

INSERT INTO reimari_turvalaitekomponentti (id, lisatiedot, turvalaitenro, "komponentti-id", sarjanumero, paivitysaika, luontiaika, luoja, muokkaaja, muokattu, alkupvm, valiaikainen, loppupvm)
VALUES ('-2139967544', 'Hieno komponentti 1', '8881', '1', '123', '2016-02-18 10:39:14.39', '2017-05-31', 'MERITAITO',
                       'MERITAITO', '2016-02-18 10:39:14.39', '2017-01-01', FALSE, '2017-12-12');
INSERT INTO reimari_turvalaitekomponentti (id, lisatiedot, turvalaitenro, "komponentti-id", sarjanumero, paivitysaika, luontiaika, luoja, muokkaaja, muokattu, alkupvm, valiaikainen, loppupvm)
VALUES ('-2139967545', 'Hieno komponentti 2', '8882', '1', '123', '2016-02-18 10:39:14.39', '2017-05-31', 'MERITAITO',
                       'MERITAITO', '2016-02-18 10:39:14.39', '2017-01-01', FALSE, '2017-12-12');
INSERT INTO reimari_turvalaitekomponentti (id, lisatiedot, turvalaitenro, "komponentti-id", sarjanumero, paivitysaika, luontiaika, luoja, muokkaaja, muokattu, alkupvm, valiaikainen, loppupvm)
VALUES ('-2139967546', 'Hieno komponentti 3', '8883', '1', '123', '2016-02-18 10:39:14.39', '2017-05-31', 'MERITAITO',
                       'MERITAITO', '2016-02-18 10:39:14.39', '2017-01-01', FALSE, '2017-12-12');
INSERT INTO reimari_turvalaitekomponentti (id, lisatiedot, turvalaitenro, "komponentti-id", sarjanumero, paivitysaika, luontiaika, luoja, muokkaaja, muokattu, alkupvm, valiaikainen, loppupvm)
VALUES ('-2139967547', 'Hieno komponentti 4', '8884', '1', '123', '2016-02-18 10:39:14.39', '2017-05-31', 'MERITAITO',
                       'MERITAITO', '2016-02-18 10:39:14.39', '2017-01-01', FALSE, '2017-12-12');
