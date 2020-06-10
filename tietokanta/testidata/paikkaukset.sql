INSERT INTO paikkauskohde ("luoja-id",
                           luotu,
                           "ulkoinen-id",
                           nimi,
                           "urakka-id",
                           tila,
                           "ilmoitettu-virhe",
                           muokattu,
                           "muokkaaja-id",
                           tarkistettu,
                           "tarkistaja-id"
)
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
  LIMIT 1)),
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
  tyomenetelmat                TEXT [] := '{"massapintaus", "kuumennuspintaus", "remix-pintaus"}';
BEGIN
  INSERT INTO paikkaus ("luoja-id", luotu, "muokkaaja-id", muokattu, "poistaja-id", poistettu, "urakka-id", "paikkauskohde-id",
                        "ulkoinen-id", alkuaika, loppuaika, tierekisteriosoite, tyomenetelma, massatyyppi, leveys, massamenekki,
                        raekoko, kuulamylly, sijainti)
  VALUES -- 20 tien paikkaukset
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_id,
                      6661, NOW() + INTERVAL '1 day', NOW() + INTERVAL '10 day',
     ROW (20, 1, 1, 1, 100, NULL) :: TR_OSOITE,
     'massapintaus', 'asfalttibetoni', 1.3, 2, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 1, 1, 1, 100))),

    (destia_kayttaja, NOW() + TIME '00:01:00', NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_id,
                      6662, NOW() + INTERVAL '5 day', NOW() +
                                                      INTERVAL '15 day', ROW (20, 1, 50, 1, 150, NULL) :: TR_OSOITE,
     'massapintaus', 'asfalttibetoni', 1.4, 3, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 1, 50, 1, 150))),

    (destia_kayttaja, NOW() + TIME '00:02:00', NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_id,
                      6663, NOW() + INTERVAL '10 day', NOW() +
                                                       INTERVAL '20 day', ROW (20, 3, 1, 3, 200, NULL) :: TR_OSOITE,
     'massapintaus', 'asfalttibetoni', 1.2, 4, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 3, 1, 3, 200))),
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_id,
                      6664, NOW() - INTERVAL '1 day', NOW() +
                                                      INTERVAL '9 day', ROW (20, 1, 50, 1, 150, NULL) :: TR_OSOITE,
     'massapintaus', 'asfalttibetoni', 1.3, 2, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 1, 50, 1, 150))),
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_id,
                      6665, NOW() - INTERVAL '1 day', NOW() +
                                                      INTERVAL '9 day', ROW (20, 3, 100, 3, 250, NULL) :: TR_OSOITE,
     'massapintaus', 'asfalttibetoni', 1.3, 2, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 3, 100, 3, 250))),
    (destia_kayttaja, NOW(), NULL, NULL, destia_kayttaja, TRUE, oulun_alueurakan_id, hoito_paikkauskohde_id,
          6666, NOW() - INTERVAL '1 day', NOW() +
                                          INTERVAL '9 day', ROW (20, 3, 100, 3, 250, NULL) :: TR_OSOITE,
          'massapintaus', 'asfalttibetoni', 1.3, 2, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 3, 100, 3, 250))),
    (destia_kayttaja, NOW() + TIME '00:03:00', NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_2_id,
                      133, NOW() + INTERVAL '10 day', NOW() +
                                                      INTERVAL '20 day', ROW (20, 3, 200, 3, 300, NULL) :: TR_OSOITE,
     'massapintaus', 'asfalttibetoni', 1.2, 4, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 3, 200, 3, 300))),

    (skanska_kayttaja, NOW(), NULL, NULL, NULL, FALSE, muhoksen_paallystysurakan_id, paallystys_paikkauskohde_id,
                       733, NOW(), NOW() + INTERVAL '20 day', ROW (20, 19, 1, 19, 50, NULL) :: TR_OSOITE,
     'massapintaus', 'asfalttibetoni', 1.2, 4, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(20, 19, 1, 19, 50))),
    -- 22 tien paikkaukset
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_22_id,
                      221, NOW() - INTERVAL '1 day', NOW() +
                                                     INTERVAL '9 day', ROW (22, 3, 1, 3, 100, NULL) :: TR_OSOITE,
     'massapintaus', 'asfalttibetoni', 1.3, 2, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(22, 3, 1, 3, 100))),
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_22_id,
                      222, NOW() - INTERVAL '1 day', NOW() +
                                                     INTERVAL '9 day', ROW (22, 3, 200, 3, 300, NULL) :: TR_OSOITE,
     'massapintaus', 'asfalttibetoni', 1.3, 2, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(22, 3, 200, 3, 300))),
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_22_id,
                      223, NOW() - INTERVAL '1 day', NOW() +
                                                     INTERVAL '9 day', ROW (22, 3, 400, 3, 450, NULL) :: TR_OSOITE,
     'massapintaus', 'asfalttibetoni', 1.3, 2, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(22, 3, 400, 3, 450))),
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_22_id,
                      224, NOW() - INTERVAL '1 day', NOW() + INTERVAL '9 day', ROW (22, 4, 1, 5, 1, NULL) :: TR_OSOITE,
     'massapintaus', 'asfalttibetoni', 1.3, 2, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(22, 4, 1, 5, 1))),
 -- Tehdään paikkaus jolle ei ole paikkaustoteumaa
    (destia_kayttaja, NOW(), NULL, NULL, NULL, FALSE, oulun_alueurakan_id, hoito_paikkauskohde_3_id,
     225, NOW() - INTERVAL '1 day', NOW() + INTERVAL '9 day', ROW (22, 4, 1, 5, 1, NULL) :: TR_OSOITE,
     'massapintaus', 'asfalttibetoni', 1.3, 2, 1, 'AN7', (SELECT tierekisteriosoitteelle_viiva(22, 4, 1, 5, 1)));
  --- Laitetaan iso kasa paikkauksia Muhoksen päällystysurakkaan. Näkee sivutuksen tällä tapaa.
  FOR counter IN 1..250 LOOP
    INSERT INTO paikkaus ("luoja-id", luotu, "muokkaaja-id", muokattu, "poistaja-id", poistettu, "urakka-id", "paikkauskohde-id",
                          "ulkoinen-id", alkuaika, loppuaika, tierekisteriosoite, tyomenetelma, massatyyppi, leveys, massamenekki,
                          raekoko, kuulamylly, sijainti)
    VALUES (skanska_kayttaja, NOW(), NULL, NULL, NULL, FALSE, muhoksen_paallystysurakan_id, paallystys_paikkauskohde_id,
                              733 + counter, NOW(), NOW() + INTERVAL '20 day',
            ROW (20, 19, (50 + counter), 19, (51 + counter), NULL) :: TR_OSOITE,
            tyomenetelmat [(counter % 3 + 1)], 'asfalttibetoni', 1.2, 4, 1, 'AN7',
            (SELECT tierekisteriosoitteelle_viiva(20, 19, (50 + counter), 19, (51 + counter))));
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
          'Liikennejärjestelyt', 3500, 'massapintaus', NOW()::DATE, ROW (20, 1, 50, 1, 150, NULL)),
         (6662, oulun_alueurakan_id, hoito_paikkauskohde_2_id, NULL, destia_kayttaja, 'kokonaishintainen',
          'Liikennejärjestelyt 2', 400, 'massapintaus', NOW()::DATE, ROW (4, 1, 20, 1, 150, NULL)),
         (133, oulun_alueurakan_id, hoito_paikkauskohde_3_id, NULL, destia_kayttaja, 'kokonaishintainen',
          'Liikennejärjestelyt', 700, 'massapintaus', NOW()::DATE, ROW (22, 1, 40, 1, 150, NULL)),
         (2355, muhoksen_paallystysurakan_id, paallystys_paikkauskohde_id, NULL, destia_kayttaja, 'kokonaishintainen',
          'Liikennejärjestelyt', 1700, 'massapintaus', NOW()::DATE, ROW (22, 1, 40, 1, 150, NULL)),
         (2359, muhoksen_paallystysurakan_id, paallystys_paikkauskohde_id, NULL, destia_kayttaja, 'kokonaishintainen',
          'Liikennejärjestelyt', 1300, 'massapintaus', NOW()::DATE, ROW (22, 1, 151, 1, 250, NULL)),
         (2356, muhoksen_paallystysurakan_id, paallystys_paikkauskohde_id, NULL, destia_kayttaja, 'kokonaishintainen',
          'Liikennejärjestelyt', 1800, 'remix-pintaus', NOW()::DATE, ROW (22, 1, 40, 1, 150, NULL)),
  (2357, muhoksen_paallystysurakan_id, paallystys_paikkauskohde_id, NULL, destia_kayttaja, 'kokonaishintainen',
      'Liikennejärjestelyt', 1900, 'kuumennuspintaus', NOW()::DATE, ROW (22, 1, 40, 1, 150, NULL));
END $$;
