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

-- Lapin MHU urakat

DO $$
DECLARE
  toimenpide_koodit TEXT[] := ARRAY['20107','20191','23104','23116','23124','14301','23151'];
  tyypit TEXT[] := ARRAY['kokonaishintainen'];
  urakat INT[] := (SELECT array_agg(id)
                   FROM urakka
                   WHERE nimi IN ('Rovaniemen MHU testiurakka (1. hoitovuosi)', 'Ivalon MHU testiurakka (uusi)', 'Pellon MHU testiurakka (3. hoitovuosi)', 'Kemin MHU testiurakka (5. hoitovuosi)'));
  toimenpide_koodi_ TEXT;
  tyyppi_ TEXT;
  urakka_ INT;
  toimenpide_nimi_ TEXT;
  toimenpide_id_ INT;
  maksueran_nimi_ TEXT;
  toimenpideinstanssi_ INT;
BEGIN
  FOREACH toimenpide_koodi_ IN ARRAY toimenpide_koodit
  LOOP
    toimenpide_nimi_ = (SELECT nimi FROM toimenpidekoodi WHERE taso = 3 AND koodi=toimenpide_koodi_);
    toimenpide_id_ = (SELECT id FROM toimenpidekoodi WHERE taso = 3 AND koodi=toimenpide_koodi_);
    FOREACH urakka_ IN ARRAY urakat
    LOOP
      FOREACH tyyppi_ IN ARRAY tyypit
      LOOP
        maksueran_nimi_ = (SELECT toimenpide_nimi_ || ':' || tyyppi_);
        toimenpideinstanssi_ = (SELECT id FROM toimenpideinstanssi WHERE urakka = urakka_ AND toimenpide = toimenpide_id_);
        INSERT INTO maksuera (toimenpideinstanssi, luotu, nimi, likainen, tyyppi)
        VALUES (toimenpideinstanssi_,
                NOW(),
                maksueran_nimi_,
                FALSE,
                tyyppi_::MAKSUERATYYPPI);
        INSERT INTO kustannussuunnitelma (maksuera) VALUES ((SELECT numero
                                                             FROM maksuera
                                                             WHERE nimi = maksueran_nimi_ AND
                                                                   toimenpideinstanssi = toimenpideinstanssi_));
      END LOOP;
    END LOOP;
  END LOOP;
END $$;