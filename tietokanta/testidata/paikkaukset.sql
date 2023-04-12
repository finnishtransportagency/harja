INSERT INTO paikkauskohde ("luoja-id", luotu, "ulkoinen-id", nimi, "urakka-id", "yhalahetyksen-tila",
                           "ilmoitettu-virhe", muokattu, "muokkaaja-id", tarkistettu, "tarkistaja-id", lisatiedot)
VALUES ((SELECT id
         FROM kayttaja
         WHERE kayttajanimi = 'yit-rakennus'
         LIMIT 1),
        current_timestamp,
        666,
        'Testikohde',
        (SELECT id
         FROM urakka
         WHERE sampoid = '1242141-OULU2'),
        'lahetetty'::lahetyksen_tila,
        NULL,
        NOW() + INTERVAL '2 day',
        (SELECT id
           FROM kayttaja
          WHERE kayttajanimi = 'jvh'
          LIMIT 1),
       NOW(),
(SELECT id
   FROM kayttaja
  WHERE kayttajanimi = 'jvh'
  LIMIT 1), 'Oulun testipaikkauskohde'),
  ((SELECT id
    FROM kayttaja
    WHERE kayttajanimi = 'destia'
    LIMIT 1),
   current_timestamp,
  666,
  'Testikohde toisessa urakassa sama urakoitsija ja ulkoinen id',
  (SELECT id
    FROM urakka
    WHERE sampoid = '1245142-KAJ2'),
   NULL,
   NULL,
   NULL,
   NULL,
   NULL,
   NULL,
   NULL),
  ((SELECT id
    FROM kayttaja
    WHERE kayttajanimi = 'destia'
    LIMIT 1),
   current_timestamp,
   1337,
   'Testikohde 2',
   (SELECT id
    FROM urakka
    WHERE sampoid = '1242141-OULU2'),
   NULL,
   NULL,
   NULL,
   NULL,
   NULL,
   NULL,
   NULL),
   ((SELECT id
       FROM kayttaja
      WHERE kayttajanimi = 'destia'
      LIMIT 1),
    current_timestamp,
    1338,
    'Testikohde 3',
    (SELECT id
       FROM urakka
      WHERE sampoid = '1242141-OULU2'),
    NULL,
    'Testikohteen numero XYZ tiedot eivät pitäneet paikkaansa. Tsekkaisitko ja lähetä korjaukset. T. Pete',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL),
  ((SELECT id
    FROM kayttaja
    WHERE kayttajanimi = 'destia'
    LIMIT 1),
   current_timestamp,
   221337,
   '22 testikohteet',
   (SELECT id
    FROM urakka
    WHERE sampoid = '1242141-OULU2'),
   NULL,
   NULL,
   NULL,
   NULL,
   NULL,
   NULL,
   NULL),
  ((SELECT id
    FROM kayttaja
    WHERE kayttajanimi = 'skanska'
    LIMIT 1),
   current_timestamp,
   7331,
   'Testikohde Muhoksen paallystysurakassa',
   (SELECT id
    FROM urakka
    WHERE sampoid = '4242523-TES2'),
   NULL,
   NULL,
   NULL,
   NULL,
   NULL,
   NULL,
   NULL);


DO $$ DECLARE
  destia_kayttaja              INTEGER := (SELECT id
                                           FROM kayttaja
                                           WHERE kayttajanimi = 'destia'
                                           LIMIT 1);
  skanska_kayttaja             INTEGER := (SELECT id
                                           FROM kayttaja
                                           WHERE kayttajanimi = 'skanska'
                                           LIMIT 1);
  oulun_alueurakan_id          INTEGER := (SELECT id
                                           FROM urakka
                                           WHERE sampoid = '1242141-OULU2');
  muhoksen_paallystysurakan_id INTEGER := (SELECT id
                                           FROM urakka
                                           WHERE sampoid = '4242523-TES2');
  hoito_paikkauskohde_id       INTEGER := (SELECT id
                                           FROM paikkauskohde
                                           WHERE "ulkoinen-id" = 666
                                           LIMIT 1);
  hoito_paikkauskohde_2_id     INTEGER := (SELECT id
                                           FROM paikkauskohde
                                           WHERE "ulkoinen-id" = 1337
                                           LIMIT 1);
  hoito_paikkauskohde_3_id     INTEGER := (SELECT id
                                             FROM paikkauskohde
                                            WHERE "ulkoinen-id" = 1338
                                            LIMIT 1);

  hoito_paikkauskohde_22_id    INTEGER := (SELECT id
                                           FROM paikkauskohde
                                           WHERE "ulkoinen-id" = 221337
                                           LIMIT 1);
  paallystys_paikkauskohde_id  INTEGER := (SELECT id
                                           FROM paikkauskohde
                                           WHERE "ulkoinen-id" = 7331
                                           LIMIT 1);
  tyomen_urem                  INTEGER := (SELECT id FROM paikkauskohde_tyomenetelma WHERE lyhenne = 'UREM');
  ajourat_                     INTEGER [] := '{1, 2, 3, 4}';
  reunat_                      INTEGER [] := '{1, 2}';
  urien_valit                  INTEGER [] := '{1, 2}';
  keskisauma                   INTEGER := 1;
BEGIN
  INSERT INTO paikkaus ("luoja-id", luotu, "muokkaaja-id", muokattu, "poistaja-id", poistettu, "urakka-id", "paikkauskohde-id",
                        "ulkoinen-id", alkuaika, loppuaika, tierekisteriosoite, tyomenetelma, massatyyppi, leveys, massamenekki,
                        raekoko, kuulamylly, sijainti)
  VALUES -- 20 tien paikkaukset
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_id,
                      6661, NOW() + INTERVAL '1 day', NOW() + INTERVAL '10 day',
     ROW (20, 1, 1, 1, 100, NULL) :: TR_OSOITE,
     8, 'AB, Asfalttibetoni', 1.3, 2, 5, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 1, 1, 1, 100))),

    (destia_kayttaja, NOW() + TIME '00:01:00', NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_id,
                      6662, NOW() + INTERVAL '5 day', NOW() +
                                                      INTERVAL '15 day', ROW (20, 1, 50, 1, 150, NULL) :: TR_OSOITE,
     8, 'AB, Asfalttibetoni', 1.4, 3, 5, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 1, 50, 1, 150))),

    (destia_kayttaja, NOW() + TIME '00:02:00', NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_id,
                      6663, NOW() + INTERVAL '10 day', NOW() +
                                                       INTERVAL '20 day', ROW (20, 3, 1, 3, 200, NULL) :: TR_OSOITE,
     8, 'AB, Asfalttibetoni', 1.2, 4, 5, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 3, 1, 3, 200))),
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_id,
                      6664, NOW() - INTERVAL '1 day', NOW() +
                                                      INTERVAL '9 day', ROW (20, 1, 50, 1, 150, NULL) :: TR_OSOITE,
     8, 'AB, Asfalttibetoni', 1.3, 2, 5, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 1, 50, 1, 150))),
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_id,
                      6665, NOW() - INTERVAL '1 day', NOW() +
                                                      INTERVAL '9 day', ROW (20, 3, 100, 3, 250, NULL) :: TR_OSOITE,
     8, 'AB, Asfalttibetoni', 1.3, 2, 5, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 3, 100, 3, 250))),
    (destia_kayttaja, NOW(), NULL, NULL, destia_kayttaja, TRUE, oulun_alueurakan_id, hoito_paikkauskohde_id,
          6666, NOW() - INTERVAL '1 day', NOW() +
                                          INTERVAL '9 day', ROW (20, 3, 100, 3, 250, NULL) :: TR_OSOITE,
          8, 'AB, Asfalttibetoni', 1.3, 2, 5, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 3, 100, 3, 250))),
    (destia_kayttaja, NOW() + TIME '00:03:00', NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_2_id,
                      133, NOW() + INTERVAL '10 day', NOW() +
                                                      INTERVAL '20 day', ROW (20, 3, 200, 3, 300, NULL) :: TR_OSOITE,
     8, 'AB, Asfalttibetoni', 1.2, 4, 5, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 3, 200, 3, 300))),
    -- 22 tien paikkaukset
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_22_id,
                      221, NOW() - INTERVAL '1 day', NOW() +
                                                     INTERVAL '9 day', ROW (22, 3, 1, 3, 100, NULL) :: TR_OSOITE,
     8, 'AB, Asfalttibetoni', 1.3, 2, 5, 'AN7', (SELECT tierekisteriosoitteelle_viiva(22, 3, 1, 3, 100))),
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_22_id,
                      222, NOW() - INTERVAL '1 day', NOW() +
                                                     INTERVAL '9 day', ROW (22, 3, 200, 3, 300, NULL) :: TR_OSOITE,
     8, 'AB, Asfalttibetoni', 1.3, 2, 5, 'AN7', (SELECT tierekisteriosoitteelle_viiva(22, 3, 200, 3, 300))),
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_22_id,
                      223, NOW() - INTERVAL '1 day', NOW() +
                                                     INTERVAL '9 day', ROW (22, 3, 400, 3, 450, NULL) :: TR_OSOITE,
     8, 'AB, Asfalttibetoni', 1.3, 2, 5, 'AN7', (SELECT tierekisteriosoitteelle_viiva(22, 3, 400, 3, 450))),
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_22_id,
                      224, NOW() - INTERVAL '1 day', NOW() + INTERVAL '9 day', ROW (22, 4, 1, 5, 1, NULL) :: TR_OSOITE,
     8, 'AB, Asfalttibetoni', 1.3, 2, 5, 'AN7', (SELECT tierekisteriosoitteelle_viiva(22, 4, 1, 5, 1))),
 -- Tehdään paikkaus jolle ei ole paikkaustoteumaa
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_3_id,
     225, NOW() - INTERVAL '1 day', NOW() + INTERVAL '9 day', ROW (22, 4, 1, 5, 1, NULL) :: TR_OSOITE,
     8, 'AB, Asfalttibetoni', 1.3, 2, 5, 'AN7', (SELECT tierekisteriosoitteelle_viiva(22, 4, 1, 5, 1)));
  --- Laitetaan iso kasa paikkauksia Muhoksen päällystysurakkaan. Näkee sivutuksen tällä tapaa.
  FOR counter IN 1..250 LOOP
    INSERT INTO paikkaus ("luoja-id", luotu, "muokkaaja-id", muokattu, "poistaja-id", poistettu, "urakka-id", "paikkauskohde-id",
                          "ulkoinen-id", alkuaika, loppuaika, tierekisteriosoite, tyomenetelma, massatyyppi, leveys, massamenekki, massamaara, "pinta-ala",
                          raekoko, kuulamylly, sijainti)
    VALUES (skanska_kayttaja, NOW(), NULL, NULL, NULL, FALSE, muhoksen_paallystysurakan_id, paallystys_paikkauskohde_id,
                              733 + counter, NOW(), NOW() + INTERVAL '20 day',
            ROW (20, 19, (50 + counter), 19, (51 + counter), NULL) :: TR_OSOITE,
            tyomen_urem,  'AB, Asfalttibetoni', 1.2, 23, 0.0276, 1.2, 5, 'AN7',
            (SELECT tierekisteriosoitteelle_viiva(20, 19, (50 + counter), 19, (51 + counter))));
    INSERT INTO paikkauksen_tienkohta ("paikkaus-id", ajorata, reunat, ajourat, ajouravalit, keskisaumat)
    VALUES ((select max(id) from paikkaus), 0,
            (CASE
                 WHEN counter > 0 AND counter < 51 THEN ARRAY[reunat_[(counter % 2 + 1)]]
                END),
            (CASE
                 WHEN counter > 50 AND counter < 101 THEN ARRAY[ajourat_[(counter % 3 + 1)]]
                END),
            (CASE
                 WHEN counter > 100 AND counter < 181 THEN ARRAY[urien_valit[(counter % 2 + 1)]]
                END),
            (CASE
                 WHEN counter > 180 THEN ARRAY[keskisauma]
                END));
      END LOOP;

  INSERT INTO paikkauksen_materiaali ("paikkaus-id", esiintyma, "kuulamylly-arvo", muotoarvo, sideainetyyppi, pitoisuus,
                                      "lisa-aineet")
  VALUES -- 20 tien paikkaukset
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 6661
      LIMIT 1), 'Testikivi', '1', 'Muotoarvo', '20/30', 3.2,
     'Lisäaineet'),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 6661
      LIMIT 1), 'Testikivi', '1', 'Muotoarvo2', '35/50', 3.2,
     'Lisäaineet'),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 6662
      LIMIT 1), 'Testikivi', '1', 'Muotoarvo', '20/30', 3.3,
     'Lisäaineet'),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 6663
      LIMIT 1), 'Testikivi', '1', 'Muotoarvo', '20/30', 3.1,
     'Lisäaineet'),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 6664
      LIMIT 1), 'Testikivi', '1', 'Muotoarvo', '20/30', 3.2,
     'Lisäaineet'),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 6665
      LIMIT 1), 'Testikivi', '1', 'Muotoarvo', '20/30', 3.2,
     'Lisäaineet'),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 133
      LIMIT 1), 'Testikivi', '1', 'Muotoarvo', '20/30', 3.1,
     'Lisäaineet'),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 733
      LIMIT 1), 'Testikivi', '1', 'Muotoarvo', '20/30', 3.1,
     'Lisäaineet'),
    -- 22 tien paikkaukset
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 221
      LIMIT 1), 'Testikivi', '1', 'Muotoarvo', '20/30', 3.2,
     'Lisäaineet'),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 222
      LIMIT 1), 'Testikivi', '1', 'Muotoarvo', '20/30', 3.2,
     'Lisäaineet'),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 223
      LIMIT 1), 'Testikivi', '1', 'Muotoarvo', '20/30', 3.2,
     'Lisäaineet'),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 224
      LIMIT 1), 'Testikivi', '1', 'Muotoarvo', '20/30', 3.2,
     'Lisäaineet');

  INSERT INTO paikkauksen_tienkohta ("paikkaus-id", ajorata, reunat, ajourat, ajouravalit, keskisaumat)
  VALUES -- 20 tien paikkaukset
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 6661
      LIMIT 1), 1, ARRAY [1], ARRAY [1], ARRAY [1], NULL),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 6662
      LIMIT 1), 2, ARRAY [1], ARRAY [1], ARRAY [1], NULL),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 6663
      LIMIT 1), 1, ARRAY [1], ARRAY [1, 2], ARRAY [1], ARRAY [1]),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 6664
      LIMIT 1), 2, ARRAY [1], ARRAY [2], NULL, NULL),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 6665
      LIMIT 1), 1, ARRAY [1], ARRAY [2], NULL, NULL),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 133
      LIMIT 1), 1, ARRAY [1], ARRAY [1, 2], ARRAY [1], ARRAY [1]),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 733
      LIMIT 1), 1, ARRAY [1], ARRAY [1, 2], ARRAY [1], ARRAY [1]),
    -- 22 tien paikkaukset
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 221
      LIMIT 1), 1, ARRAY [1], ARRAY [1, 2], ARRAY [1], NULL),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 222
      LIMIT 1), 1, ARRAY [1], ARRAY [1, 2], ARRAY [1], NULL),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 223
      LIMIT 1), 1, ARRAY [2], ARRAY [1, 2], ARRAY [1], NULL),
    ((SELECT id
      FROM paikkaus
      WHERE "ulkoinen-id" = 224
      LIMIT 1), 1, ARRAY [2], ARRAY [1, 2], ARRAY [1], NULL);

  INSERT INTO paikkaustoteuma ("ulkoinen-id", "urakka-id", "paikkauskohde-id", "toteuma-id",
                               "luoja-id", tyyppi, selite, hinta, tyomenetelma, valmistumispvm,
                               tierekisteriosoite)
  VALUES -- Kokonaishintaiset
         (6661, oulun_alueurakan_id, hoito_paikkauskohde_id, NULL, destia_kayttaja, 'kokonaishintainen',
          'Liikennejärjestelyt', 3500, 8, NOW()::DATE, ROW (20, 1, 50, 1, 150, NULL)),
         (6662, oulun_alueurakan_id, hoito_paikkauskohde_2_id, NULL, destia_kayttaja, 'kokonaishintainen',
          'Liikennejärjestelyt 2', 400, 8, NOW()::DATE, ROW (4, 1, 20, 1, 150, NULL)),
         (133, oulun_alueurakan_id, hoito_paikkauskohde_3_id, NULL, destia_kayttaja, 'kokonaishintainen',
          'Liikennejärjestelyt', 700, 8, NOW()::DATE, ROW (22, 1, 40, 1, 150, NULL)),
         (2355, muhoksen_paallystysurakan_id, paallystys_paikkauskohde_id, NULL, destia_kayttaja, 'kokonaishintainen',
          'Liikennejärjestelyt', 1700, 8, NOW()::DATE, ROW (22, 1, 40, 1, 150, NULL)),
         (2359, muhoksen_paallystysurakan_id, paallystys_paikkauskohde_id, NULL, destia_kayttaja, 'kokonaishintainen',
          'Liikennejärjestelyt', 1300, 8, NOW()::DATE, ROW (22, 1, 151, 1, 250, NULL)),
         (2356, muhoksen_paallystysurakan_id, paallystys_paikkauskohde_id, NULL, destia_kayttaja, 'kokonaishintainen',
          'Liikennejärjestelyt', 1800, 6, NOW()::DATE, ROW (22, 1, 40, 1, 150, NULL)),
  (2357, muhoksen_paallystysurakan_id, paallystys_paikkauskohde_id, NULL, destia_kayttaja, 'kokonaishintainen',
      'Liikennejärjestelyt', 1900, 4, NOW()::DATE, ROW (22, 1, 40, 1, 150, NULL));
END $$;


-- Lisätään Kemin päällystysurkalle muutama paikkauskohde testiä varten
insert into paikkauskohde (nimi, luotu, "luoja-id", "urakka-id", alkupvm, loppupvm, "paikkauskohteen-tila", "ulkoinen-id",
                           tyomenetelma, tierekisteriosoite_laajennettu, "suunniteltu-maara", "suunniteltu-hinta", yksikko) VALUES
('Kaislajärven suora', current_timestamp, 3, (SELECT id FROM urakka WHERE nimi = 'Kemin päällystysurakka'),
 '2021-06-01', '2021-06-13', 'ehdotettu', 999888773, 6,
 ROW (926, 5, 2764, 6, 2964, 0, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu, 100, 100, 't');

insert into paikkauskohde (nimi, luotu, "luoja-id", "urakka-id", alkupvm, loppupvm, "paikkauskohteen-tila", "ulkoinen-id",
                           tyomenetelma, tierekisteriosoite_laajennettu, tilattupvm, "suunniteltu-maara", "suunniteltu-hinta", yksikko) VALUES
('Kaislajärven suora osa 1', current_timestamp, 3, (SELECT id FROM urakka WHERE nimi = 'Kemin päällystysurakka'),
 '2021-05-01', '2021-05-13', 'tilattu', 999888774, 11,
 ROW (926, 6, 2964, 7, 3064, 0, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu, '2021-02-17', 200, 200, 'm2');

insert into paikkauskohde (nimi, luotu, "luoja-id", "urakka-id", alkupvm, loppupvm, "paikkauskohteen-tila", "ulkoinen-id",
                           tyomenetelma, tierekisteriosoite_laajennettu, "suunniteltu-maara", "suunniteltu-hinta", yksikko,
                           tilattupvm, valmistumispvm) VALUES
('Kaislajärven suora osa 2', current_timestamp, 3, (SELECT id FROM urakka WHERE nimi = 'Kemin päällystysurakka'),
 '2021-01-01', '2021-01-13', 'valmis', 999888775, 11,
 ROW (926, 7, 3164, 8, 3264, 0, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu, 300, 300, 'kpl', '2021-01-13', '2021-01-12');

insert into paikkauskohde (nimi, luotu, "luoja-id", "urakka-id", alkupvm, loppupvm, "paikkauskohteen-tila", "ulkoinen-id",
                           tyomenetelma, tierekisteriosoite_laajennettu, "suunniteltu-maara", "suunniteltu-hinta", yksikko, lisatiedot) VALUES
('Kaislajärven suora osa 3', current_timestamp, 3, (SELECT id FROM urakka WHERE nimi = 'Kemin päällystysurakka'),
 '2021-03-01', '2021-03-13', 'hylatty', 999888776, 4,
 ROW (926, 9, 3364, 12, 3964, 0, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu,
 400, 400, 'jm', 'Keskustelujen jälkeen päädyttiin siihen, että tätä kohtaa ei tarvitse paikata.');

-- Lisätään UREM kohde ja sille yksi toteuma
insert into paikkauskohde ("ulkoinen-id", nimi, luotu, "luoja-id", "urakka-id",
                           alkupvm, loppupvm, tyomenetelma, tierekisteriosoite_laajennettu,
                           "paikkauskohteen-tila", "suunniteltu-maara", "suunniteltu-hinta", yksikko, lisatiedot)  VALUES
(999888777, 'Muokattava testikohde', current_timestamp, 3, (SELECT id FROM urakka WHERE nimi = 'Kemin päällystysurakka'), '2021-01-01', '2021-01-02', 8,
 ROW(926, 9, 3364, 12, 3964, 1, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu, 'tilattu',
 1000, 1000, 'jm', 'muokattava testikohde');

INSERT INTO paikkaus ("urakka-id", "paikkauskohde-id", "ulkoinen-id", alkuaika, loppuaika, tierekisteriosoite,
                      tyomenetelma, massatyyppi, leveys, raekoko, kuulamylly, massamaara)
VALUES (36,(select id from paikkauskohde where nimi = 'Muokattava testikohde'),1113, '2021-01-01 00:00:00',
        '2021-01-01 01:00:00', ROW(926, 9, 3364, 12, 3964, NULL)::tr_osoite, 8, 'AB, Asfalttibetoni', 7.1, 5, 'AN5', 1234);

-- Tähän levitinpaikkauskohde ja paikkaus, jotta voidaan testata menetelmäspesifit käyttöliittymäasiat nopeammin
INSERT INTO paikkauskohde ("luoja-id", "ulkoinen-id", nimi, poistettu, luotu, "muokkaaja-id", muokattu, "urakka-id", "yhalahetyksen-tila", tarkistettu, "tarkistaja-id", "ilmoitettu-virhe", alkupvm, loppupvm, tilattupvm, tyomenetelma, tierekisteriosoite_laajennettu, "paikkauskohteen-tila", "suunniteltu-maara", "suunniteltu-hinta", yksikko, lisatiedot, "pot?", valmistumispvm, tiemerkintapvm, "toteutunut-hinta", "tiemerkintaa-tuhoutunut?", takuuaika, "yllapitokohde-id") VALUES (3, 234, 'Levitinpaikkaus', false, '2023-01-05 09:35:56.413000', 3, '2023-01-05 09:35:56.413000', (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'), null, null, null, null, '2023-01-05', '2023-02-24', '2023-01-05', 1, ROW(4,101,1,101,7000,1, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu, 'tilattu', 100, 20000, 'm2', 'Levitinpaikkaustestikohde.', false, null, null, null, null, null, null);

INSERT INTO paikkaus ("luoja-id", luotu, "muokkaaja-id", muokattu, "poistaja-id", poistettu, "urakka-id", "paikkauskohde-id", "ulkoinen-id", alkuaika, loppuaika, tierekisteriosoite, tyomenetelma, massatyyppi, leveys, raekoko, kuulamylly, sijainti, massamaara, "pinta-ala", juoksumetri, kpl, lahde, massamenekki) VALUES (3, '2023-01-05 09:45:41.566362', 3, '2023-01-05 09:46:10.003000', null, false, (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka'), (SELECT id FROM paikkauskohde WHERE nimi = 'Levitinpaikkaus'), 0, '2023-01-05 00:00:00.000000', '2023-02-24 00:00:00.000000', ROW(4,101,1,101,1001,NULL)::tr_osoite, 1, 'PAB-B, Pehmeät asfalttibetonit', 4, 5, 'AN5', '0105000000010000000102000000280000006EB1DE0E038D1741DBBB6DFD627359417E8CB96B2B8D174117D9CE6761735941F7E461E1768D174188F4DBF360735941992A18D5C18D1741D712F2B56173594155302A29C68E17419E5E297F6C73594196438B6C638F174152B81EDD6C7359416519E2180B911741D1915C526D735941C74B3709AD921741E86A2BE26D735941A5BDC1171794174136AB3E376E735941C7293A1259941741CC7F487B6E73594154742457BF9417419E5E29AF6E735941B30C716C24951741D42B65B56F735941D9CEF753319517413F355ECA71735941E86A2BB62D951741341136A07973594199BB96902E9517416F8104B17973594104E78CE83D951741C3F5283C7B735941AD69DE71509517416519E24C7C735941DE718A4E74951741539621127D73594107CE1951AA951741F241CF927D73594148BF7D9DF2951741454772E57D735941273108AC2F961741D26F5F177E735941B6F3FDD46E9617418126C24A7E7359412DB29D6FF6961741304CA6A67E7359413D0AD7A38297174177BE9F027F735941696FF08598971741508D970E7F7359414D158C4A129817415452274C7F7359415917B7D172981741BE30995E7F7359414850FCD8D7981741A1F8317E7F735941C898BB96249917412F6EA325807359414625754254991741F6285C4F81735941711B0D20689917412575024682735941D8F0F4CA7B9917412DB29DFF83735941D734EF78B799174100000048897359413411367CE0991741C9E53F708C73594188635D9C109A17410B24285A907359416E348037559A1741D34D627095735941ED0DBE70739A1741B840829E97735941D95F760F879A1741DE718ACA99735941F9A067F38F9A174164CC5D8F9D735941BC707D5D919A1741848EE24FA0735941', 100, null, null, null, 'harja-ui', 25);

INSERT INTO paikkauksen_tienkohta ("paikkaus-id", ajorata, reunat, ajourat, ajouravalit, keskisaumat, kaista) VALUES ((SELECT id FROM paikkaus WHERE "urakka-id" = (SELECT id FROM urakka WHERE nimi = 'Muhoksen päällystysurakka') AND tierekisteriosoite = ROW(4,101,1,101,1001,NULL)::tr_osoite), 1, null, null, null, null, 11);

-- Koska todella monessa paikkauksessa ei alunperin lisätty paikkauskohteelle paikkauskohde-tila arvoa eikä työmenetelmää
-- niin päivitetään ne tässä viimeiseksi. Tämä on tarpeellinen sen vuoksi, että flyway migraatioissa ajetut päivitykset
-- on tehty ennen kuin näitä testiaineistoja ajetaan kantaan.
-- Päivitetään "vanhoille" paikkauskohteille työmenetelmät paikkaustoteumien perusteella
UPDATE paikkauskohde pk
SET tyomenetelma = (SELECT p.tyomenetelma
                    FROM paikkaus p
                    WHERE p."paikkauskohde-id" = pk.id
                      AND p.tyomenetelma IS NOT NULL
                    ORDER BY p.id DESC
                    LIMIT 1),
    alkupvm = (SELECT MIN(p.alkuaika)
                   FROM paikkaus p
                   WHERE p."paikkauskohde-id" = pk.id),
    loppupvm =  (SELECT MAX(p.loppuaika)
                 FROM paikkaus p
                 WHERE p."paikkauskohde-id" = pk.id)
WHERE pk.tyomenetelma IS NULL;

-- Päivitetään "vanhoille" paikkauskohteille paikkauskohde-tila -> valmis, jotta niitäkin voidaan tarkistella
-- paikkauskohdelistauksessa
UPDATE paikkauskohde pk
SET "paikkauskohteen-tila" = 'valmis',
    tilattupvm =  (SELECT MAX(p.loppuaika)
                   FROM paikkaus p
                   WHERE p."paikkauskohde-id" = pk.id),
    valmistumispvm =  (SELECT MAX(p.loppuaika)
                       FROM paikkaus p
                       WHERE p."paikkauskohde-id" = pk.id)
WHERE pk."paikkauskohteen-tila" IS NULL;
