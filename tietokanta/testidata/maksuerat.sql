-- Maksuerät Oulun alueurakalle 2005-2010
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (1, 'kokonaishintainen', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (1, 'yksikkohintainen', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (1, 'lisatyo', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (1, 'indeksi', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (1, 'bonus', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (1, 'sakko', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (1, 'akillinen-hoitotyo', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (1, 'muu', 'Oulu Talvihoito TP' );

INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (2, 'kokonaishintainen', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (2, 'yksikkohintainen', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (2, 'lisatyo', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (2, 'indeksi', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (2, 'bonus', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (2, 'sakko', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (2, 'akillinen-hoitotyo', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (2, 'muu', 'Oulu Sorateiden hoito TP' );

-- Kustannussuunnitelmat Oulun alueurakalle 2005-2010
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 1 AND tyyppi = 'kokonaishintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 1 AND tyyppi = 'yksikkohintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 1 AND tyyppi = 'lisatyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 1 AND tyyppi = 'indeksi'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 1 AND tyyppi = 'bonus'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 1 AND tyyppi = 'sakko'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 1 AND tyyppi = 'akillinen-hoitotyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 1 AND tyyppi = 'muu'));

INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 2 AND tyyppi = 'kokonaishintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 2 AND tyyppi = 'yksikkohintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 2 AND tyyppi = 'lisatyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 2 AND tyyppi = 'indeksi'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 2 AND tyyppi = 'bonus'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 2 AND tyyppi = 'sakko'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 2 AND tyyppi = 'akillinen-hoitotyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 2 AND tyyppi = 'muu'));

-- Maksuerät Oulun alueurakalle 2014-2019
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (4, 'kokonaishintainen', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (4, 'yksikkohintainen', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (4, 'lisatyo', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (4, 'indeksi', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (4, 'bonus', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (4, 'sakko', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (4, 'akillinen-hoitotyo', 'Oulu Talvihoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (4, 'muu', 'Oulu Talvihoito TP' );

INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (5, 'kokonaishintainen', 'Oulu Liikenneympäristön hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (5, 'yksikkohintainen', 'Oulu Liikenneympäristön hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (5, 'lisatyo', 'Oulu Liikenneympäristön hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (5, 'indeksi', 'Oulu Liikenneympäristön hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (5, 'bonus', 'Oulu Liikenneympäristön hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (5, 'sakko', 'Oulu Liikenneympäristön hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (5, 'akillinen-hoitotyo', 'Oulu Liikenneympäristön hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (5, 'muu', 'Oulu Liikenneympäristön hoito TP' );

INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (6, 'kokonaishintainen', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (6, 'yksikkohintainen', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (6, 'lisatyo', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (6, 'indeksi', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (6, 'bonus', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (6, 'sakko', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (6, 'akillinen-hoitotyo', 'Oulu Sorateiden hoito TP' );
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi) VALUES (6, 'muu', 'Oulu Sorateiden hoito TP' );


-- Kustannussuunnitelmat Oulun alueurakalle 2014-2019
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 4 AND tyyppi = 'kokonaishintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 4 AND tyyppi = 'yksikkohintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 4 AND tyyppi = 'lisatyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 4 AND tyyppi = 'indeksi'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 4 AND tyyppi = 'bonus'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 4 AND tyyppi = 'sakko'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 4 AND tyyppi = 'akillinen-hoitotyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 4 AND tyyppi = 'muu'));

INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 5 AND tyyppi = 'kokonaishintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 5 AND tyyppi = 'yksikkohintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 5 AND tyyppi = 'lisatyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 5 AND tyyppi = 'indeksi'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 5 AND tyyppi = 'bonus'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 5 AND tyyppi = 'sakko'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 5 AND tyyppi = 'akillinen-hoitotyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 5 AND tyyppi = 'muu'));

INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 6 AND tyyppi = 'kokonaishintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 6 AND tyyppi = 'yksikkohintainen'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 6 AND tyyppi = 'lisatyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 6 AND tyyppi = 'indeksi'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 6 AND tyyppi = 'bonus'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 6 AND tyyppi = 'sakko'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 6 AND tyyppi = 'akillinen-hoitotyo'));
INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero FROM maksuera WHERE toimenpideinstanssi = 6 AND tyyppi = 'muu'));
