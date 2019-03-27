DO $$ DECLARE
  hel_itainen_vaylahoitourakka_id INTEGER := (SELECT id FROM urakka WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL');
  vantaa_itainen_vaylahoitourakka_id INTEGER := (SELECT id FROM urakka WHERE nimi ILIKE 'Vantaan väyläyksikön väylänhoito ja -käyttö, Itäinen SL');
  hel_vayla_paasopimus_id INTEGER := (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus');
  kayttaja_id_tero INTEGER := (SELECT id FROM kayttaja WHERE kayttajanimi = 'tero');
  hietasaaren_poiju_turvalaitenro TEXT := (SELECT turvalaitenro FROM vatu_turvalaite WHERE nimi = 'Hietasaaren poiju');
  hietasaaren_viitta_turvalaitenro TEXT := (SELECT turvalaitenro FROM vatu_turvalaite WHERE nimi = 'Hietasaaren viitta');
  testitoimenpide_2_id INTEGER := 0;
  testitoimenpiteen_hinnoittelu_id INTEGER := 0;
  venepojat_organisaatio_id INTEGER := (SELECT id FROM organisaatio WHERE nimi = 'Pohjanmeren venepojat');
  poijukorjaus_toimenpide_id INTEGER := 0;
  poijukorjaus_hinnoittelu_id INTEGER := 0;
BEGIN

-- ***********************************************
-- VIKA ILMAN KORJAUSTA
-- ***********************************************


INSERT INTO vv_vikailmoitus
("reimari-id", "reimari-lisatiedot", "reimari-turvalaitenro", "reimari-ilmoittaja", "reimari-ilmoittajan-yhteystieto",
 "reimari-epakunnossa?", "reimari-tyyppikoodi", "reimari-tilakoodi",
 "reimari-havaittu", "reimari-kirjattu", "reimari-muokattu", "reimari-luontiaika", "reimari-luoja", "reimari-muokkaaja")
VALUES
  ('1234', 'Akonniemen kyltti on lähtenyt irti myrskyn takia',
           (SELECT turvalaitenro FROM vatu_turvalaite WHERE nimi = 'Akonniemen kyltti'),
           'ilmari vikailmoittaja', -- reimari-ilmoittaja
           'ilmari.vi@example.com +55 5555 5555 5555 555 55', -- reimari-ilmoittajan-yhteystiedot
           TRUE, -- reimari-epakunnossa?
           '8478478', -- tyyppikoodi
           '8884848', -- tilakoodi
           '2017-05-01T12:12:12', -- havaittu
           '2017-05-01T13:13:13', -- kirjattu
           '2017-05-01T13:13:13', -- muokattu
   '2017-05-01T13:13:13', -- luontiaika
   'lauri luoja', -- luoja
   'mikko muokkaaja' -- muokkaaja
  );

-- ***********************************************
-- KOKONAISHINTAINEN TOIMENPIDE KIINTIÖSSÄ
-- ***********************************************

INSERT INTO vv_kiintio
("urakka-id", "sopimus-id", nimi, koko, luoja)
VALUES
  (hel_itainen_vaylahoitourakka_id,
   hel_vayla_paasopimus_id,
   'Siirtyneiden poijujen siirto',
   30,
   kayttaja_id_tero);

INSERT INTO vv_kiintio
("urakka-id", "sopimus-id", nimi, koko, luoja)
VALUES
  (vantaa_itainen_vaylahoitourakka_id,
   hel_vayla_paasopimus_id,
   'Joku kiintiö Vantaalla',
   30,
   kayttaja_id_tero);

INSERT INTO vv_kiintio
("urakka-id", "sopimus-id", nimi, koko, luoja, poistettu)
VALUES
  (hel_itainen_vaylahoitourakka_id,
   hel_vayla_paasopimus_id,
   'POISTETTU KIINTIÖ EI SAA NÄKYÄ',
   999999,
   kayttaja_id_tero,
   TRUE);

-- Tyhjä kiintiö
INSERT INTO vv_kiintio
("urakka-id", "sopimus-id", nimi, koko, luoja)
VALUES
  (hel_itainen_vaylahoitourakka_id,
   hel_vayla_paasopimus_id,
   'Aurinkopaneelien akkujen vaihto',
   5,
   kayttaja_id_tero);

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
 "reimari-id",
 "kiintio-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-komponentit",
 "reimari-turvalaite",
 "turvalaitenro",
 lisatieto,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vaylanro")
VALUES
  ('kokonaishintainen',
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    2,
    (SELECT id
     FROM vv_kiintio
     WHERE nimi = 'Siirtyneiden poijujen siirto'),
    '(23, Pohjanmeren venepojat)',
    venepojat_organisaatio_id,
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus, NULL)',
    hel_vayla_paasopimus_id,
     '{"(-2139967544,nimitahan,1022540401)","(-2139967548,toinennimi,1022540402)"}', -- reimari-komponentit (id, nimi, tila)
    '(8881, Poiju 1, 3332)',
    (SELECT turvalaitenro FROM vatu_turvalaite WHERE nimi = 'Hietasaaren poiju'),
    'Kiintiöön kuuluva jutska',
    '2017-08-05T23:23Z',
    '2017-08-05',
    kayttaja_id_tero,
    '2017-08-05',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542001',
    '1022541802',
    '1022541905',
    '(123,Hietasaaren läntinen rinnakkaisväylä,55)',
   (SELECT vaylanro
    FROM vv_vayla
    WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

-- ***********************************************
-- KOKONAISHINTAISET TOIMENPITEET ILMAN VIKAA
-- ***********************************************

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaitenro",
 lisatieto,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vaylanro")
VALUES
  ('kokonaishintainen',
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    12,
    '(23, Pohjanmeren venepojat)',
    venepojat_organisaatio_id,
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus,)',
    hel_vayla_paasopimus_id,
    '(8882, Poiju 2, 3332)',
    (SELECT turvalaitenro FROM vatu_turvalaite WHERE nimi = 'Hietasaaren poiju'),
    NULL,
    '2017-08-05T23:23Z',
    '2017-08-05',
    kayttaja_id_tero,
    '2017-08-05',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542001',
    '1022541802',
    '1022541905',
    '(123,Hietasaaren läntinen rinnakkaisväylä,55)',
    (SELECT vaylanro
     FROM vv_vayla
     WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaitenro",
 lisatieto,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vaylanro")
VALUES
  ('kokonaishintainen',
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    22,
    '(23, Pohjanmeren venepojat)',
    venepojat_organisaatio_id,
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus,)',
    hel_vayla_paasopimus_id,
    '(12345, Poiju 3, 3331)',
    hietasaaren_poiju_turvalaitenro,
    NULL,
    '2017-08-08T23:23Z',
    '2017-08-08',
    kayttaja_id_tero,
    '2017-08-08',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542049',
    '1022541802',
    '1022541903',
    '(123,Akonniemen väylät,55)',
    (SELECT vaylanro
     FROM vv_vayla
     WHERE nimi = 'Akonniemen väylät'));

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaitenro",
 lisatieto,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "reimari-henkilo-lkm",
 "vaylanro")
VALUES
  ('kokonaishintainen',
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    32,
    '(23, Pohjanmeren venepojat)',
    venepojat_organisaatio_id,
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus,)',
    hel_vayla_paasopimus_id,
    '(8884, Poiju 4, 3332)',
    (SELECT turvalaitenro FROM vatu_turvalaite WHERE nimi = 'Hietasaaren poiju'),
    NULL,
    '2017-08-08T23:23Z',
    '2017-08-08',
    kayttaja_id_tero,
    '2017-08-08',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542049',
    '1022541803',
    '1022541903',
    '(123,Akonniemen väylät,55)',
    30,
   (SELECT vaylanro
    FROM vv_vayla
    WHERE nimi = 'Akonniemen väylät'));

-- ***********************************************
-- KOKONAISHINTAISET TOIMENPITEET VIALLA
-- ***********************************************

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaitenro",
 lisatieto,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vaylanro")
VALUES
  ('kokonaishintainen',
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    42,
    '(23, Pohjanmeren venepojat)',
    venepojat_organisaatio_id,
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus,)',
    hel_vayla_paasopimus_id,
    '(1234, Poiju 6, 3331)',
    hietasaaren_viitta_turvalaitenro,
    'TESTITOIMENPIDE 2',
    '2017-04-04T23:23Z',
    '2017-04-04',
    kayttaja_id_tero,
    '2017-04-04',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542001',
    '1022541802',
    '1022541905',
    '(123,Hietasaaren läntinen rinnakkaisväylä,55)',
    (SELECT vaylanro
     FROM vv_vayla
     WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

testitoimenpide_2_id := (select id from reimari_toimenpide where lisatieto = 'TESTITOIMENPIDE 2');

INSERT INTO liite (nimi, tyyppi, lahde) VALUES ('vv_liite.jpg', 'image/jpeg', 'harja-ui' :: LAHDE);
INSERT INTO liite (nimi, tyyppi, lahde) VALUES ('vv_liite2.jpg', 'image/jpeg', 'harja-ui' :: LAHDE);
INSERT INTO liite (nimi, tyyppi, lahde) VALUES ('vv_liite3.jpg', 'image/jpeg', 'harja-ui' :: LAHDE);
INSERT INTO liite (nimi, tyyppi, lahde) VALUES ('POISTETTU LIITE EI SAA NÄKYÄ', 'image/jpeg', 'harja-ui' :: LAHDE);
INSERT INTO reimari_toimenpide_liite ("toimenpide-id", "liite-id") VALUES (testitoimenpide_2_id,
                                                                           (SELECT id
                                                                            FROM liite
                                                                            WHERE nimi = 'vv_liite.jpg'));
INSERT INTO reimari_toimenpide_liite ("toimenpide-id", "liite-id") VALUES (testitoimenpide_2_id,
                                                                           (SELECT id
                                                                            FROM liite
                                                                            WHERE nimi = 'vv_liite2.jpg'));
INSERT INTO reimari_toimenpide_liite ("toimenpide-id", "liite-id", poistettu) VALUES (testitoimenpide_2_id,
                                                                                      (SELECT id
                                                                                       FROM liite
                                                                                       WHERE nimi =
                                                                                             'POISTETTU LIITE EI SAA NÄKYÄ'),
                                                                                      TRUE);

UPDATE reimari_toimenpide SET "reimari-viat" = '{"(123,8884848)"}'
WHERE id = (SELECT id FROM reimari_toimenpide WHERE lisatieto = 'TESTITOIMENPIDE 2');

  INSERT INTO vv_vikailmoitus
  ("reimari-id", "reimari-lisatiedot", "reimari-turvalaitenro", "reimari-ilmoittaja", "reimari-ilmoittajan-yhteystieto",
   "reimari-epakunnossa?", "reimari-tyyppikoodi", "reimari-tilakoodi",
   "reimari-havaittu", "reimari-kirjattu", "reimari-muokattu", "reimari-luontiaika", "reimari-luoja", "reimari-muokkaaja",
   "toimenpide-id")
  VALUES
    ('123', 'Hietasaaren viitta on kaatunut',
            hietasaaren_viitta_turvalaitenro,
            'ilmari vikailmoittaja', -- reimari-ilmoittaja
            'ilmari.vi@example.com +55 5555 5555 5555 555 55', -- reimari-ilmoittajan-yhteystiedot
            TRUE, -- reimari-epakunnossa?
            '8478478', -- tyyppikoodi
            '8884848', -- tilakoodi
            '2017-04-02T12:12:12', -- havaittu
            '2017-04-02T13:13:13', -- kirjattu
            '2017-04-02T13:13:13', -- muokattu
     '2017-04-02T13:13:13', -- luontiaika
     'lauri luoja', -- luoja
     'mikko muokkaaja', -- muokkaaja
     testitoimenpide_2_id);

-- ***********************************************
-- KOKONAISHINTAISIIN SIIRRETYT, REIMARISTA YKSIKKÖHINTAISENA RAPORTOIDUT TYÖT
-- ***********************************************

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "reimari-lisatyo",
 "urakka-id",
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaitenro",
 lisatieto,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vaylanro")

VALUES
  ('kokonaishintainen',
    TRUE,
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    52,
    '(23, Pohjanmeren venepojat)',
    venepojat_organisaatio_id,
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus,)',
    hel_vayla_paasopimus_id,
    '(8890, Poiju 10, 3332)',
    (SELECT turvalaitenro FROM vatu_turvalaite WHERE nimi = 'Hietasaaren poiju'),
    NULL,
    '2017-08-03T23:23Z',
    '2017-08-03',
    kayttaja_id_tero,
    '2017-08-03',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542001',
    '1022541802',
    '1022541905',
    '(123,Hietasaaren läntinen rinnakkaisväylä,55)',
   (SELECT vaylanro
    FROM vv_vayla
    WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

-- ***********************************************
-- YKSIKKÖHINTAISIIN SIIRRETYT TYÖT, ILMAN HINTAERITTELYÄ
-- ***********************************************

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaitenro",
 lisatieto,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vaylanro")
VALUES
  ('yksikkohintainen',
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    62,
    '(23, Pohjanmeren venepojat)',
    venepojat_organisaatio_id,
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus,)',
    hel_vayla_paasopimus_id,
    '(8891, Poiju 11, 3332)',
    (SELECT turvalaitenro FROM vatu_turvalaite WHERE nimi = 'Hietasaaren poiju'),
    NULL,
    '2017-12-01T23:23Z',
    '2017-08-08',
    kayttaja_id_tero,
    '2017-08-08',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542001',
    '1022541802',
    '1022541905',
    '(123,Hietasaaren läntinen rinnakkaisväylä,55)',
    (SELECT vaylanro
     FROM vv_vayla
     WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

-- ***********************************************
-- TODO: YKSIKKÖHINTAISIIN SIIRRETTY TYÖ, HINTAERITTELY MATERIAALEIHIN/KOMPONENTTEIHIN/TÖIHIN TEHTY
-- ***********************************************


-- ***********************************************
-- REIMARISTA YKSIKKÖHINTAISENA RAPORTOITU TYÖ
-- ***********************************************

INSERT INTO reimari_toimenpide
(hintatyyppi,
  "reimari-komponentit",
 "reimari-lisatyo",
 "urakka-id",
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaitenro",
 lisatieto,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
  "reimari-henkilo-lkm",
 "vaylanro")
VALUES
  ('yksikkohintainen',
    '{"(-2139967544,nimitahan,1022540401)","(-2139967548,toinennimi,1022540402)"}',
    TRUE,
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    82,
    '(23, Pohjanmeren venepojat)',
    venepojat_organisaatio_id,
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus,)',
    hel_vayla_paasopimus_id,
    '(8881, Poiju 1, 3332)',
    (SELECT turvalaitenro FROM vatu_turvalaite WHERE nimi = 'Hietasaaren pienempi poiju'),
    'Poijujen korjausta kuten on sovittu otos 2',
    '2017-11-02T23:23Z',
    '2017-08-08',
    kayttaja_id_tero,
    '2017-08-08',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542001',
    '1022541802',
    '1022541905',
    '(123,Hietasaaren läntinen rinnakkaisväylä,55)',
    3,
   (SELECT vaylanro
    FROM vv_vayla
    WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä'));

poijukorjaus_toimenpide_id := (SELECT id FROM reimari_toimenpide WHERE lisatieto = 'Poijujen korjausta kuten on sovittu otos 2');

INSERT INTO reimari_toimenpide_liite ("toimenpide-id", "liite-id") VALUES (poijukorjaus_toimenpide_id,
                                                                           (SELECT id
                                                                            FROM liite
                                                                            WHERE nimi = 'vv_liite3.jpg'));

-- ***********************************************
-- REIMARISTA YKSIKKÖHINTAISENA RAPORTOITU, KÖNTTÄSUMMALLA
-- SEKÄ TARKENNETULLA HINNALLA HINNOITELTU TOIMENPIDE
-- ***********************************************

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "urakka-id",
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaitenro",
 lisatieto,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "vaylanro")
VALUES
  ('yksikkohintainen',
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    72,
    '(23, Pohjanmeren venepojat)',
    venepojat_organisaatio_id,
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus,)',
    hel_vayla_paasopimus_id,
    '(8881, Poiju 1, 3332)',
    (SELECT turvalaitenro FROM vatu_turvalaite WHERE nimi = 'Hietasaaren pienempi poiju'),
    'Poijujen korjausta kuten on sovittu',
    '2017-10-03T23:23Z',
    '2017-08-08',
    kayttaja_id_tero,
    '2017-08-08',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542001',
    '1022541802',
    '1022541905',
    '(123,Hietasaaren läntinen rinnakkaisväylä,55)',
    (SELECT vaylanro
     FROM vv_vayla
     WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä')),
  ('yksikkohintainen',
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    73,
    '(23, Pohjanmeren venepojat)',
    venepojat_organisaatio_id,
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus,)',
    hel_vayla_paasopimus_id,
    '(8881, Poiju 1, 3332)',
    (SELECT turvalaitenro FROM vatu_turvalaite WHERE nimi = 'Hietasaaren pienempi poiju'),
    'Lisää poijujen korjausta',
    '2017-09-04T23:23Z',
    '2017-08-08',
    kayttaja_id_tero,
    '2017-08-08',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542001',
    '1022541802',
    '1022541905',
    '(123,Hietasaaren läntinen rinnakkaisväylä,55)',
    (SELECT vaylanro
     FROM vv_vayla
     WHERE nimi = 'Hietasaaren läntinen rinnakkaisväylä')),
  ('yksikkohintainen',
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
    74,
    '(23, Pohjanmeren venepojat)',
    venepojat_organisaatio_id,
    '(-5, 1022542301, Helsingin väyläyksikön pääsopimus,)',
    hel_vayla_paasopimus_id,
    '(666, Poiju 666, 555)',
    (SELECT turvalaitenro FROM vatu_turvalaite WHERE nimi = 'Hietasaaren pienempi poiju'),
    'Oulaisten poijujen korjaus',
    '2017-08-05T23:23Z',
    '2017-08-08',
    kayttaja_id_tero,
    '2017-08-08',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542001',
    '1022541802',
    '1022541905',
    '(123,Hietasaaren läntinen rinnakkaisväylä,55)',
    (SELECT vaylanro
     FROM vv_vayla
     WHERE nimi = 'Oulaisten meriväylä'));

-- Hintaryhmät
INSERT INTO vv_hinnoittelu
(nimi, hintaryhma, luoja, "urakka-id")
VALUES
  ('Hietasaaren poijujen korjaus', TRUE,
   kayttaja_id_tero,
   (SELECT "urakka-id"
    FROM reimari_toimenpide
    WHERE lisatieto = 'Poijujen korjausta kuten on sovittu'));

INSERT INTO vv_hinnoittelu
(nimi, hintaryhma, luoja, "urakka-id")
VALUES
  ('Oulaisten meriväylän kunnostus', TRUE,
   kayttaja_id_tero,
   (SELECT "urakka-id"
    FROM reimari_toimenpide
    WHERE lisatieto = 'Oulaisten poijujen korjaus'));
INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja)
VALUES
  ((SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'Hietasaaren poijujen korjaus'),
   'Ryhmähinta', 60000, (SELECT id
                         FROM kayttaja
                         WHERE kayttajanimi = 'tero'));
INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja)
VALUES
  ((SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'Oulaisten meriväylän kunnostus'),
   'Ryhmähinta', 30, (SELECT id
                      FROM kayttaja
                      WHERE kayttajanimi = 'tero'));
INSERT INTO vv_hinnoittelu_kommentti
(tila, aika, "laskutus-pvm", "kayttaja-id", "hinnoittelu-id")
  VALUES
    ('hyvaksytty'::kommentin_tila,
    '2018-07-01'::DATE - INTERVAL '2 months',
     '2018-07-01'::DATE - INTERVAL '2 months',
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'),
     (SELECT id
      FROM vv_hinnoittelu
      WHERE nimi = 'Oulaisten meriväylän kunnostus'));
INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id", luoja)
VALUES
  ((SELECT id
    FROM reimari_toimenpide
    WHERE lisatieto = 'Poijujen korjausta kuten on sovittu'),
   (SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'Hietasaaren poijujen korjaus'),
   kayttaja_id_tero);
INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id", luoja)
VALUES
  ((SELECT id
    FROM reimari_toimenpide
    WHERE lisatieto = 'Lisää poijujen korjausta'),
   (SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'Hietasaaren poijujen korjaus'),
   kayttaja_id_tero);
INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id", luoja)
VALUES
  ((SELECT id
    FROM reimari_toimenpide
    WHERE lisatieto = 'Oulaisten poijujen korjaus'),
   (SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'Oulaisten meriväylän kunnostus'),
   kayttaja_id_tero);
-- Toimenpiteen hintatiedot
INSERT INTO vv_hinnoittelu
(nimi, hintaryhma, luoja, "urakka-id")
VALUES
  ('Toimenpiteen oma hinnoittelu', FALSE,
   kayttaja_id_tero,
   (SELECT "urakka-id"
    FROM reimari_toimenpide
    WHERE lisatieto = 'Poijujen korjausta kuten on sovittu'));
testitoimenpiteen_hinnoittelu_id := (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Toimenpiteen oma hinnoittelu');
INSERT INTO vv_hinnoittelu
(nimi, hintaryhma, luoja, "urakka-id", poistettu)
VALUES
  ('POISTETTU HINNOITTELU EI SAISI NÄKYÄ MISSÄÄN', FALSE,
   kayttaja_id_tero,
   (SELECT "urakka-id"
    FROM reimari_toimenpide
    WHERE lisatieto = 'TESTITOIMENPIDE 2'), TRUE);
INSERT INTO vv_hinnoittelu
(nimi, hintaryhma, luoja, "urakka-id")
VALUES
  ('Vantaan urakan testihinnoittelu', TRUE,
   kayttaja_id_tero,
   vantaa_itainen_vaylahoitourakka_id);
INSERT INTO vv_hinnoittelu
(nimi, hintaryhma, luoja, "urakka-id")
VALUES
  ('Hietasaaren poijujen korjausta otos 2', FALSE,
   kayttaja_id_tero,
   (SELECT id
    FROM urakka
    WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'));
poijukorjaus_hinnoittelu_id := (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Hietasaaren poijujen korjausta otos 2');
INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja, ryhma)
VALUES
  (testitoimenpiteen_hinnoittelu_id,
   'Yleiset materiaalit', 600,
   kayttaja_id_tero, 'muu');
INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja, ryhma)
VALUES
  (testitoimenpiteen_hinnoittelu_id,
   'Käyttäjän itse kirjaama juttu', 1,
   kayttaja_id_tero, 'muu');
INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja, ryhma)
VALUES
  (testitoimenpiteen_hinnoittelu_id,
   'Omakustannushinta', 150,
   kayttaja_id_tero, 'tyo');
INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja, poistettu, ryhma)
VALUES
  (testitoimenpiteen_hinnoittelu_id,
   'POISTETTU HINTA EI SAA NÄKYÄ', 99999999,
   kayttaja_id_tero, TRUE, 'muu');
INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja, poistettu, ryhma)
VALUES
  ((SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'Hietasaaren poijujen korjaus'),
   'HINTARYHMÄÄN LIITTYVÄ POISTETTU HINTA EI SAA NÄKYÄ', 99999999,
   kayttaja_id_tero, TRUE, 'muu');
INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja, poistettu, ryhma)
VALUES
  ((SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'POISTETTU HINNOITTELU EI SAISI NÄKYÄ MISSÄÄN'),
   'POISTETTUUN HINNOITTELUUN KUULUVA HINTA JOKA EI OLE POISTETTU EI SAA NÄKYÄ MISSÄÄN', 99999999,
   (SELECT id
    FROM kayttaja
    WHERE kayttajanimi =
          'tero'),
   TRUE, 'muu');
INSERT INTO vv_tyo
("hinnoittelu-id", maara, luoja, "toimenpidekoodi-id")
VALUES
  (testitoimenpiteen_hinnoittelu_id,
   30,
   kayttaja_id_tero,
   (SELECT id
    FROM toimenpidekoodi
    WHERE nimi = 'Henkilöstö: Ammattimies' AND koodi ILIKE('VV%')));
INSERT INTO vv_tyo
("hinnoittelu-id", maara, luoja, "toimenpidekoodi-id")
VALUES
  (testitoimenpiteen_hinnoittelu_id,
   15,
   kayttaja_id_tero,
   (SELECT id
    FROM toimenpidekoodi
    WHERE nimi = 'Henkilöstö: Työnjohto' AND koodi ILIKE('VV%')));
INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja, ryhma)
VALUES
  ((SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'Vantaan urakan testihinnoittelu'),
   'Yleiset materiaalit', 5, (SELECT id
                              FROM kayttaja
                              WHERE kayttajanimi = 'tero'), 'muu');
INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja, ryhma)
VALUES
  (poijukorjaus_hinnoittelu_id,
   'Yleiset materiaalit', 70, (SELECT id
                               FROM kayttaja
                               WHERE kayttajanimi = 'tero'), 'muu');
INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja, ryhma, yksikkohinta, yksikko, maara, "komponentti-id", "komponentti-tilamuutos")
VALUES
  (poijukorjaus_hinnoittelu_id,
   'KOMPONENTIN OTSIKKO EI NÄY', NULL, (SELECT id
                               FROM kayttaja
                               WHERE kayttajanimi = 'tero'), 'komponentti',
  1500, 'kpl', 1, (SELECT id FROM reimari_turvalaitekomponentti WHERE lisatiedot = 'Hieno komponentti 1'),
  '1022540401');
INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id", luoja)
VALUES
  ((SELECT id
    FROM reimari_toimenpide
    WHERE lisatieto = 'Poijujen korjausta kuten on sovittu'),
   testitoimenpiteen_hinnoittelu_id,
   kayttaja_id_tero);
INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id", luoja)
VALUES
  (testitoimenpide_2_id,
   (SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'POISTETTU HINNOITTELU EI SAISI NÄKYÄ MISSÄÄN'),
   kayttaja_id_tero);
INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id", luoja)
VALUES
  (poijukorjaus_toimenpide_id,
   poijukorjaus_hinnoittelu_id,
   kayttaja_id_tero);
-- Sisävesiväylät, Pyhäselän ja Rentoselän urakka
INSERT INTO vv_hinnoittelu
(nimi, hintaryhma, luoja, "urakka-id")
VALUES
  ('Pyhäselän poijujen korjaus', TRUE,
   (SELECT id
    FROM kayttaja
    WHERE kayttajanimi = 'tero'),
   (SELECT id
    FROM urakka
    WHERE nimi ILIKE 'Pyhäselän urakka'));
INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja)
VALUES
  ((SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'Pyhäselän poijujen korjaus'),
   'Ryhmähinta', 60000, (SELECT id
                         FROM kayttaja
                         WHERE kayttajanimi = 'tero'));

INSERT INTO reimari_toimenpide
(hintatyyppi,
 "reimari-komponentit",
 "reimari-lisatyo",
 "urakka-id",
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaite-id",
 lisatieto,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "reimari-henkilo-lkm",
 "vaylanro")
VALUES
  ('yksikkohintainen',
    '{"(-2139967544,nimitahan,1022540401)","(-2139967548,toinennimi,1022540402)"}',
    TRUE,
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Pyhäselän urakka'),
    998,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-25, 1022542301, Pyhäselän pääsopimus,)',
    (SELECT id
     FROM sopimus
     WHERE nimi = 'Pyhäselän pääsopimus'),
    '(8881, Poiju 1, 3332)',
    (SELECT id
     FROM vv_turvalaite
     WHERE nimi = 'Pyhäselän pienempi poiju'),
    'Poijujen korjausta kuten on sovittu Pyhäselkä',
    '2016-08-08T23:23Z',
    '2016-08-08',
    (SELECT id
     FROM kayttaja
     WHERE kayttajanimi = 'tero'),
    '2016-08-08',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542001',
    '1022541802',
    '1022541905',
   '(123,Pyhäselän läntinen rinnakkaisväylä,55)',
   3,
   (SELECT vaylanro
    FROM vv_vayla
    WHERE nimi = 'Pyhäselän läntinen rinnakkaisväylä'));

INSERT INTO vv_hinnoittelu
(nimi, hintaryhma, luoja, "urakka-id")
VALUES
  ('Rentoselän poijujen korjaus', TRUE,
   (SELECT id
    FROM kayttaja
    WHERE kayttajanimi = 'tero'),
   (SELECT id
    FROM urakka
    WHERE nimi ILIKE 'Rentoselän urakka'));

INSERT INTO vv_hinta
("hinnoittelu-id", otsikko, summa, luoja)
VALUES
  ((SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'Rentoselän poijujen korjaus'),
   'Ryhmähinta', 60000, (SELECT id
                         FROM kayttaja
                         WHERE kayttajanimi = 'tero'));


INSERT INTO reimari_toimenpide
(hintatyyppi,
 "reimari-komponentit",
 "reimari-lisatyo",
 "urakka-id",
 "reimari-id",
 "reimari-urakoitsija",
 "urakoitsija-id",
 "reimari-sopimus",
 "sopimus-id",
 "reimari-turvalaite",
 "turvalaite-id",
 lisatieto,
 suoritettu,
 luotu,
 luoja,
 "reimari-luotu",
 "reimari-alus",
 "reimari-tila",
 "reimari-toimenpidetyyppi",
 "reimari-tyolaji",
 "reimari-tyoluokka",
 "reimari-vayla",
 "reimari-henkilo-lkm",
 "vaylanro")
VALUES
  ('yksikkohintainen',
    '{"(-2139967544,nimitahan,1022540401)","(-2139967548,toinennimi,1022540402)"}',
    TRUE,
    (SELECT id
     FROM urakka
     WHERE nimi ILIKE 'Rentoselän urakka'),
    999,
    '(23, Pohjanmeren venepojat)',
    (SELECT id
     FROM organisaatio
     WHERE nimi = 'Pohjanmeren venepojat'),
    '(-26, 1022542301, Rentoselän pääsopimus,)',
    (SELECT id
     FROM sopimus
     WHERE nimi = 'Rentoselän pääsopimus'),
    '(8881, Poiju 1, 3332)',
    (SELECT id
     FROM vv_turvalaite
     WHERE nimi = 'Rentoselän pienempi poiju'),
    'Poijujen korjausta kuten on sovittu Rentoselkä',
    '2016-08-08T23:23Z',
    '2016-08-08',
    (SELECT id
     FROM kayttaja
     WHERE kayttajanimi = 'tero'),
    '2016-08-08',
    '(MBKE24524, MS Piggy)',
    '1022541202',
    '1022542001',
    '1022541802',
    '1022541905',
   '(123,Rentoselän läntinen rinnakkaisväylä,55)',
   3,
   (SELECT vaylanro
    FROM vv_vayla
    WHERE nimi = 'Rentoselän läntinen rinnakkaisväylä'));


INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id", luoja)
VALUES
  ((SELECT id
    FROM reimari_toimenpide
    WHERE lisatieto = 'Poijujen korjausta kuten on sovittu Pyhäselkä'),
   (SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'Pyhäselän poijujen korjaus'),
   (SELECT id
    FROM kayttaja
    WHERE kayttajanimi = 'tero'));

INSERT INTO vv_hinnoittelu_toimenpide
("toimenpide-id", "hinnoittelu-id", luoja)
VALUES
  ((SELECT id
    FROM reimari_toimenpide
    WHERE lisatieto = 'Poijujen korjausta kuten on sovittu Rentoselkä'),
   (SELECT id
    FROM vv_hinnoittelu
    WHERE nimi = 'Rentoselän poijujen korjaus'),
   (SELECT id
    FROM kayttaja
    WHERE kayttajanimi = 'tero'));
INSERT INTO vv_hinnoittelu_kommentti
(tila, aika, "laskutus-pvm", "kayttaja-id", "hinnoittelu-id")
VALUES
  ('hyvaksytty'::kommentin_tila,
   '2018-07-01'::DATE - INTERVAL '3 months',
   '2018-07-01'::DATE - INTERVAL '2 months',
   (SELECT id
    FROM kayttaja
    WHERE kayttajanimi = 'tero'),
   (SELECT oman_hinnoittelun_id
    FROM vv_toimenpiteen_hinnoittelut
    WHERE toimenpiteen_id = (SELECT id
                             FROM reimari_toimenpide
                             WHERE lisatieto = 'Poijujen korjausta kuten on sovittu')
          AND oman_hinnoittelun_id IS NOT NULL));
INSERT INTO reimari_toimenpide
(hintatyyppi,
 "reimari-komponentit",
 "urakka-id",
 "sopimus-id",
 suoritettu,
 luotu,
 luoja,
 "harjassa-luotu",
lisatieto)
VALUES
  ('yksikkohintainen',
    '{}',
    hel_itainen_vaylahoitourakka_id,
    hel_vayla_paasopimus_id,
    '2017-08-08T23:23Z',
    '2017-08-08',
    (SELECT id
     FROM kayttaja
     WHERE kayttajanimi = 'tero'),
    TRUE,
  'Harjassa luotu 1');

  INSERT INTO vv_hinnoittelu
  (nimi, hintaryhma, luoja, "urakka-id")
  VALUES
    ('Harjassa luotu 1 oma hinta', FALSE,
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'),
     hel_itainen_vaylahoitourakka_id);

  INSERT INTO vv_hinnoittelu_toimenpide
  ("toimenpide-id", "hinnoittelu-id", luoja)
  VALUES
    ((SELECT id
      FROM reimari_toimenpide
      WHERE lisatieto = 'Harjassa luotu 1'),
     (SELECT id
      FROM vv_hinnoittelu
      WHERE nimi = 'Harjassa luotu 1 oma hinta'),
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'));

  INSERT INTO vv_hinnoittelu_kommentti
  (tila, aika, "laskutus-pvm", "kayttaja-id", "hinnoittelu-id")
  VALUES
    ('hyvaksytty'::kommentin_tila,
     '2017-08-08',
     '2017-08-08',
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'),
     (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Harjassa luotu 1 oma hinta'));

  INSERT INTO vv_hinta
  ("hinnoittelu-id", otsikko, summa, luoja, ryhma)
  VALUES
    ((SELECT id
      FROM vv_hinnoittelu
      WHERE nimi = 'Harjassa luotu 1 oma hinta'),
     'Yleiset materiaalit', 500,
     kayttaja_id_tero, 'muu');

  INSERT INTO reimari_toimenpide
  (hintatyyppi,
   "reimari-komponentit",
   "urakka-id",
   "sopimus-id",
   suoritettu,
   luotu,
   luoja,
   "harjassa-luotu",
   lisatieto)
  VALUES
    ('yksikkohintainen',
     '{}',
     hel_itainen_vaylahoitourakka_id,
     hel_vayla_paasopimus_id,
     '2017-09-09T23:23Z',
     '2017-09-09',
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'),
     TRUE,
     'Harjassa luotu 2');

  INSERT INTO vv_hinnoittelu
  (nimi, hintaryhma, luoja, "urakka-id")
  VALUES
    ('Harjassa luotu 2 oma hinta', FALSE,
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'),
     hel_itainen_vaylahoitourakka_id);

  INSERT INTO vv_hinnoittelu_toimenpide
  ("toimenpide-id", "hinnoittelu-id", luoja)
  VALUES
    ((SELECT id
      FROM reimari_toimenpide
      WHERE lisatieto = 'Harjassa luotu 2'),
     (SELECT id
      FROM vv_hinnoittelu
      WHERE nimi = 'Harjassa luotu 2 oma hinta'),
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'));

  INSERT INTO vv_hinnoittelu_kommentti
  (tila, aika, "laskutus-pvm", "kayttaja-id", "hinnoittelu-id")
  VALUES
    ('hyvaksytty'::kommentin_tila,
     '2017-10-10',
     '2017-10-10',
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'),
     (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Harjassa luotu 2 oma hinta'));

  INSERT INTO vv_hinta
  ("hinnoittelu-id", otsikko, summa, luoja, ryhma)
  VALUES
    ((SELECT id
      FROM vv_hinnoittelu
      WHERE nimi = 'Harjassa luotu 2 oma hinta'),
     'Yleiset materiaalit', 600,
     kayttaja_id_tero, 'muu');

  INSERT INTO reimari_toimenpide
  (hintatyyppi,
   "reimari-komponentit",
   "urakka-id",
   "sopimus-id",
   suoritettu,
   luotu,
   luoja,
   "harjassa-luotu",
   lisatieto)
  VALUES
    ('yksikkohintainen',
     '{}',
     hel_itainen_vaylahoitourakka_id,
     hel_vayla_paasopimus_id,
     '2017-10-10T23:23Z',
     '2017-10-10',
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'),
     TRUE,
     'Harjassa luotu 3');

  INSERT INTO vv_hinnoittelu
  (nimi, hintaryhma, luoja, "urakka-id")
  VALUES
    ('Harjassa luotu 3 oma hinta', FALSE,
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'),
     hel_itainen_vaylahoitourakka_id);

  INSERT INTO vv_hinnoittelu_toimenpide
  ("toimenpide-id", "hinnoittelu-id", luoja)
  VALUES
    ((SELECT id
      FROM reimari_toimenpide
      WHERE lisatieto = 'Harjassa luotu 3'),
     (SELECT id
      FROM vv_hinnoittelu
      WHERE nimi = 'Harjassa luotu 3 oma hinta'),
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'));

  INSERT INTO vv_hinta
  ("hinnoittelu-id", otsikko, summa, luoja, ryhma)
  VALUES
    ((SELECT id
      FROM vv_hinnoittelu
      WHERE nimi = 'Harjassa luotu 3 oma hinta'),
     'Yleiset materiaalit', 400,
     kayttaja_id_tero, 'muu');

  -- Tee harjassa luoduille toimenpiteille tilaus, ryhmähinta, ja hyväksy se

  INSERT INTO vv_hinnoittelu
  (nimi, hintaryhma, luoja, "urakka-id")
  VALUES
    ('Harjassa luotujen tilaus', TRUE,
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'),
     hel_itainen_vaylahoitourakka_id);

  INSERT INTO vv_hinnoittelu_toimenpide
  ("toimenpide-id", "hinnoittelu-id", luoja)
  VALUES
    ((SELECT id
      FROM reimari_toimenpide
      WHERE lisatieto = 'Harjassa luotu 1'),
     (SELECT id
      FROM vv_hinnoittelu
      WHERE nimi = 'Harjassa luotujen tilaus'),
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'));

  INSERT INTO vv_hinnoittelu_toimenpide
  ("toimenpide-id", "hinnoittelu-id", luoja)
  VALUES
    ((SELECT id
      FROM reimari_toimenpide
      WHERE lisatieto = 'Harjassa luotu 2'),
     (SELECT id
      FROM vv_hinnoittelu
      WHERE nimi = 'Harjassa luotujen tilaus'),
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'));

  INSERT INTO vv_hinnoittelu_toimenpide
  ("toimenpide-id", "hinnoittelu-id", luoja)
  VALUES
    ((SELECT id
      FROM reimari_toimenpide
      WHERE lisatieto = 'Harjassa luotu 3'),
     (SELECT id
      FROM vv_hinnoittelu
      WHERE nimi = 'Harjassa luotujen tilaus'),
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'));

  INSERT INTO vv_hinnoittelu_kommentti
  (tila, aika, "laskutus-pvm", "kayttaja-id", "hinnoittelu-id")
  VALUES
    ('hyvaksytty'::kommentin_tila,
     '2017-11-11',
     '2017-11-11',
     (SELECT id
      FROM kayttaja
      WHERE kayttajanimi = 'tero'),
     (SELECT id FROM vv_hinnoittelu WHERE nimi = 'Harjassa luotujen tilaus'));

  INSERT INTO vv_hinta
  ("hinnoittelu-id", otsikko, summa, luoja)
  VALUES
    ((SELECT id
      FROM vv_hinnoittelu
      WHERE nimi = 'Harjassa luotujen tilaus'),
     'Ryhmähinta', 60000, (SELECT id
                           FROM kayttaja
                           WHERE kayttajanimi = 'tero'));
END $$;
