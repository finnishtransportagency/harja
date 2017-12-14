-- Väylänhoito : Kokonaishintaiset
INSERT INTO maksuera (toimenpideinstanssi,
                      luotu,
                      nimi,
                      likainen,
                      tyyppi)
VALUES ((SELECT id
         FROM toimenpideinstanssi
         WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'),
        '2017-12-14 13:45:18.947853',
        'Väylänhoito : Kokonaishintaiset',
        TRUE,
        'kokonaishintainen');

INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero
                                                     FROM maksuera
                                                     WHERE nimi = 'Väylänhoito : Kokonaishintaiset'));

-- Väylänhoito : Yksikköhintaiset
INSERT INTO maksuera (toimenpideinstanssi,
                      luotu,
                      nimi,
                      likainen,
                      tyyppi)
VALUES ((SELECT id
         FROM toimenpideinstanssi
         WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'),
        '2017-12-14 13:45:18.947853',
        'Väylänhoito : Yksikköhintaiset',
        TRUE,
        'yksikkohintainen');

INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero
                                                     FROM maksuera
                                                     WHERE nimi = 'Väylänhoito : Yksikköhintaiset'));

-- Väylänhoito : Lisätyöt
INSERT INTO maksuera (toimenpideinstanssi,
                      luotu,
                      nimi,
                      likainen,
                      tyyppi)
VALUES ((SELECT id
         FROM toimenpideinstanssi
         WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'),
        '2017-12-14 13:45:18.947853',
        'Väylänhoito : Lisätyöt',
        TRUE,
        'lisatyo');

INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero
                                                     FROM maksuera
                                                     WHERE nimi = 'Väylänhoito : Lisätyöt'));

-- Väylänhoito : Indeksit
INSERT INTO maksuera (toimenpideinstanssi,
                      luotu,
                      nimi,
                      likainen,
                      tyyppi)
VALUES ((SELECT id
         FROM toimenpideinstanssi
         WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'),
        '2017-12-14 13:45:18.947853',
        'Väylänhoito : Indeksit',
        TRUE,
        'indeksi');
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero
                                                     FROM maksuera
                                                     WHERE nimi = 'Väylänhoito : Lisätyöt'));

--  Väylänhoito : Bonukset
INSERT INTO maksuera (toimenpideinstanssi,
                      luotu,
                      nimi,
                      likainen,
                      tyyppi)
VALUES ((SELECT id
         FROM toimenpideinstanssi
         WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'),
        '2017-12-14 13:45:18.947853',
        'Väylänhoito : Bonukset',
        TRUE,
        'bonus');

INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero
                                                     FROM maksuera
                                                     WHERE nimi = 'Väylänhoito : Bonukset'));

-- Väylänhoito : Sakot
INSERT INTO maksuera (toimenpideinstanssi,
                      luotu,
                      nimi,
                      likainen,
                      tyyppi)
VALUES ((SELECT id
         FROM toimenpideinstanssi
         WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'),
        '2017-12-14 13:45:18.947853',
        'Väylänhoito : Sakot',
        TRUE,
        'sakko');

INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero
                                                     FROM maksuera
                                                     WHERE nimi = 'Väylänhoito : Sakot'));

-- Väylänhoito : Äkilliset hoitotyöt
INSERT INTO maksuera (toimenpideinstanssi,
                      luotu,
                      nimi,
                      likainen,
                      tyyppi)
VALUES ((SELECT id
         FROM toimenpideinstanssi
         WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'),
        '2017-12-14 13:45:18.947853',
        'Väylänhoito : Äkilliset hoitotyöt',
        TRUE,
        'akillinen-hoitotyo');

INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero
                                                     FROM maksuera
                                                     WHERE nimi = 'Väylänhoito : Äkilliset hoitotyöt'));

-- Väylänhoito : Muut
INSERT INTO maksuera (toimenpideinstanssi,
                      luotu,
                      nimi,
                      likainen,
                      tyyppi)
VALUES ((SELECT id
         FROM toimenpideinstanssi
         WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'),
        '2017-12-14 13:45:18.947853',
        'Väylänhoito : Muut',
        TRUE,
        'muu');

INSERT INTO kustannussuunnitelma (maksuera)
VALUES ((SELECT numero
         FROM maksuera
         WHERE nimi = 'Väylänhoito : Muut'));
