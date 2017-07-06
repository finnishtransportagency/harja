-- ***********************************************
-- VÄYLÄT
-- ***********************************************

INSERT INTO vv_vayla
(nimi, "vatu-id", tyyppi)
VALUES
  ('Hietasaaren läntinen rinnakkaisväylä', 1, 'kauppamerenkulku'::vv_vaylatyyppi);

INSERT INTO vv_vayla
(nimi, "vatu-id", tyyppi)
VALUES
  ('Oulaisten meriväylä', 1, 'muu'::vv_vaylatyyppi);


INSERT INTO vv_vayla
(nimi, "vatu-id", tyyppi)
VALUES
  ('Akonniemen väylät', 2, 'kauppamerenkulku'::vv_vaylatyyppi);

INSERT INTO vv_vayla
(nimi, "vatu-id", tyyppi)
VALUES
  ('Muu väylä', 3, 'muu'::vv_vaylatyyppi);

-- ***********************************************
-- TURVALAITTEET
-- ***********************************************

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vayla)
    VALUES
      ('Hietasaaren pienempi poiju', '1234', 'poiju', (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vayla)
VALUES
  ('Hietasaaren poiju', '12345', 'poiju', (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vayla)
VALUES
  ('Hietasaaren viitta', '12346', 'viitta', (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vayla)
VALUES
  ('Hietasaaren kyltti', '12347', 'kiintea', (SELECT id FROM vv_vayla WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

INSERT INTO vv_turvalaite
(nimi, tunniste, tyyppi, vayla)
VALUES
  ('Akonniemen kyltti', '123', 'kiintea', (SELECT id FROM vv_vayla WHERE nimi = 'Akonniemen väylät'));

-- ***********************************************
-- TURVALAITEKOMPONENTTITYYPIT
-- ***********************************************

INSERT INTO reimari_komponenttityyppi (id, nimi, lisatiedot, "luokan-id", "luokan-nimi", "luokan-lisatiedot", "luokan-paivitysaika", "luokan-luontiaika", "merk-cod", paivitysaika, luontiaika, muokattu, alkupvm, loppupvm)
VALUES ('1', 'Lateraalimerkki', 'PV 800 oikea', '1200', 'Viitat/Lateriaalimerkit',	'Ihan kiva luokka', '2003-06-09', '2003-06-09 09:00:00', '2003-06-09 09:00:00	0', '2012-10-02', '2003-06-09 10:02:32.462',	'2012-10-02 10:02:32.462',	'2012-10-02 00:00:00', '2099-12-31 00:00:00');

INSERT INTO reimari_komponenttityyppi (id, nimi, lisatiedot, "luokan-id", "luokan-nimi", "luokan-lisatiedot", "luokan-paivitysaika", "luokan-luontiaika", "merk-cod", paivitysaika, luontiaika, muokattu, alkupvm, loppupvm)
VALUES ('2', 'Lateraalimerkki', 'PV 800 vasen', '1200', 'Viitat/Lateriaalimerkit',	'Ihan kiva luokka', '2003-06-09', '2003-06-09 09:00:00', '2003-06-09 09:00:00	0', '2012-10-02', '2003-06-09 10:02:32.462',	'2012-10-02 10:02:32.462',	'2012-10-02 00:00:00', '2099-12-31 00:00:00');

INSERT INTO reimari_komponenttityyppi (id, nimi, lisatiedot, "luokan-id", "luokan-nimi", "luokan-lisatiedot", "luokan-paivitysaika", "luokan-luontiaika", "merk-cod", paivitysaika, luontiaika, muokattu, alkupvm, loppupvm)
VALUES ('3', 'Lateraalimerkki', 'PV 800 Etelä', '1200', 'Viitat/Lateriaalimerkit',	'Ihan kiva luokka', '2003-06-09', '2003-06-09 09:00:00', '2003-06-09 09:00:00	0', '2012-10-02', '2003-06-09 10:02:32.462',	'2012-10-02 10:02:32.462',	'2012-10-02 00:00:00', '2099-12-31 00:00:00');

INSERT INTO reimari_komponenttityyppi (id, nimi, lisatiedot, "luokan-id", "luokan-nimi", "luokan-lisatiedot", "luokan-paivitysaika", "luokan-luontiaika", "merk-cod", paivitysaika, luontiaika, muokattu, alkupvm, loppupvm)
VALUES ('4', 'Lateraalimerkki', 'PV 800 Pohjoinen', '1200', 'Viitat/Lateriaalimerkit',	'Ihan kiva luokka', '2003-06-09', '2003-06-09 09:00:00', '2003-06-09 09:00:00	0', '2012-10-02', '2003-06-09 10:02:32.462',	'2012-10-02 10:02:32.462',	'2012-10-02 00:00:00', '2099-12-31 00:00:00');



-- ***********************************************
-- TURVALAITEKOMPONENTIT
-- ***********************************************

INSERT INTO reimari_turvalaitekomponentti (id, lisatiedot, turvalaitenro, "komponentti-id", sarjanumero, paivitysaika, luontiaika, luoja, muokkaaja, muokattu, alkupvm, valiaikainen, loppupvm)
VALUES ('-2139967596', 'Hieno komponentti',	'1', '1', '123', '2016-02-18 10:39:14.39',	'2017-05-31', 'MERITAITO', 'MERITAITO', '2016-02-18 10:39:14.39', '2017-01-01', false, '2017-12-12');

INSERT INTO reimari_turvalaitekomponentti (id, lisatiedot, turvalaitenro, "komponentti-id", sarjanumero, paivitysaika, luontiaika, luoja, muokkaaja, muokattu, alkupvm, valiaikainen, loppupvm)
VALUES ('-567765567', 'Hieno komponentti on',	'2', '2', '123', '2016-02-18 10:39:14.39',	'2017-05-31', 'MERITAITO', 'MERITAITO', '2016-02-18 10:39:14.39', '2017-01-01', false, '2017-12-12');

INSERT INTO reimari_turvalaitekomponentti (id, lisatiedot, turvalaitenro, "komponentti-id", sarjanumero, paivitysaika, luontiaika, luoja, muokkaaja, muokattu, alkupvm, valiaikainen, loppupvm)
VALUES ('-4556365653', 'Hieno komponentti juu',	'3', '3', '123', '2016-02-18 10:39:14.39',	'2017-05-31', 'PASISA', 'PASISA', '2016-02-18 10:39:14.39', '2017-01-01', false, '2017-12-12');

INSERT INTO reimari_turvalaitekomponentti (id, lisatiedot, turvalaitenro, "komponentti-id", sarjanumero, paivitysaika, luontiaika, luoja, muokkaaja, muokattu, alkupvm, valiaikainen, loppupvm)
VALUES ('-6785597846', 'Hieno komponentti se on',	'4', '4', '123', '2016-02-18 10:39:14.39',	'2017-05-31', 'PASISA', 'PASISA', '2016-02-18 10:39:14.39', '2017-01-01', false, '2017-12-12');
