ALTER TABLE yksikkohintainen_tyo ADD COLUMN arvioitu_kustannus NUMERIC;

INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Muut: Reunaparrutyö (painekyllästetty)', 4, now(), 'm', NULL,
        '{yksikkohintainen}', (SELECT id
                               FROM toimenpidekoodi
                               WHERE koodi='24104'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Henkilöstö: Työnjohto', 4, now(), 'h', NULL,
        '{yksikkohintainen}', (SELECT id
                               FROM toimenpidekoodi
                               WHERE koodi='24104'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Henkilöstö: Miestyö', 4, now(), 'h', NULL,
        '{yksikkohintainen}', (SELECT id
                               FROM toimenpidekoodi
                               WHERE koodi='24104'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Henkilöstö: Sukellustyö (ammattisukeltaja)', 4, now(), 'h', NULL,
        '{yksikkohintainen}', (SELECT id
                               FROM toimenpidekoodi
                               WHERE koodi='24104'));