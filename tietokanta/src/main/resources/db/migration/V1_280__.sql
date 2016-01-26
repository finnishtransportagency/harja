-- Kuvaus: Lisää toimenpidekoodiin hinnoittelu ja jarjestys -sarakkeet
CREATE TYPE hinnoittelutyyppi
as ENUM('kokonaishintainen', 'yksikkohintainen', 'muutoshintainen');

ALTER TABLE toimenpidekoodi ADD COLUMN jarjestys INTEGER;
ALTER TABLE toimenpidekoodi ADD COLUMN hinnoittelu hinnoittelutyyppi[];

UPDATE toimenpidekoodi
SET hinnoittelu = ARRAY['kokonaishintainen'::hinnoittelutyyppi]
  WHERE taso = 4 AND kokonaishintainen;

UPDATE toimenpidekoodi
SET hinnoittelu = ARRAY['muutoshintainen'::hinnoittelutyyppi]
WHERE taso = 4 AND kokonaishintainen IS NOT TRUE
      AND emo =
          (SELECT ID from toimenpidekoodi
          WHERE nimi = 'Laaja toimenpide' AND taso = 3
                AND emo =
                    (SELECT id FROM toimenpidekoodi
                    wHERE nimi = 'Talvihoito'
                          AND taso = 2));

UPDATE toimenpidekoodi
SET hinnoittelu = ARRAY['yksikkohintainen'::hinnoittelutyyppi,
'muutoshintainen'::hinnoittelutyyppi]
WHERE taso = 4 AND hinnoittelu IS NULL AND kokonaishintainen IS NOT TRUE;

ALTER TABLE toimenpidekoodi DROP COLUMN kokonaishintainen;

-- Markku Hussi email 2016-01-12 14:17 toivoi tähän järjestykseen hoitoluokat (sama järj. kuin paperilomakkeella)
UPDATE toimenpidekoodi SET jarjestys = 1000 WHERE nimi = 'Is 2-ajorat. KVL >15000' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1010 WHERE nimi = 'Is 1-ajorat. KVL >15000' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1020 WHERE nimi = 'Is ohituskaistat KVL >15000' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1030 WHERE nimi = 'Is rampit KVL >15000' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1040 WHERE nimi = 'Is 2-ajorat.' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1050 WHERE nimi = 'Is 1-ajorat.' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1060 WHERE nimi = 'Is ohituskaistat' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1070 WHERE nimi = 'Is rampit' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1080 WHERE nimi = 'I 2-ajorat.' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1090 WHERE nimi = 'I 1-ajorat.' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1100 WHERE nimi = 'I ohituskaistat' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1110 WHERE nimi = 'I rampit' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1120 WHERE nimi = 'Ib 2-ajorat.' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1130 WHERE nimi = 'Ib 1-ajorat.' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1140 WHERE nimi = 'Ib ohituskaistat' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1150 WHERE nimi = 'Ib rampit' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1160 WHERE nimi = 'TIb' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1170 WHERE nimi = 'II' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1180 WHERE nimi = 'III' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1190 WHERE nimi = 'K1' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
UPDATE toimenpidekoodi SET jarjestys = 1200 WHERE nimi = 'K2' AND emo =(SELECT ID from toimenpidekoodi WHERE nimi = 'Laaja toimenpide' AND taso = 3 AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Talvihoito' AND taso = 2));
