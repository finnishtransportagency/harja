-- Päivitä tehtävän Liikenteen varmistaminen kelirikkokohteessa toimenpidekoodin yksikkö kaikille suunnitelluille yksikköhintaisille töille
WITH toimenpidekoodi AS (SELECT
                           id,
                           yksikko,
                           nimi
                         FROM toimenpidekoodi
                         WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa' )
UPDATE yksikkohintainen_tyo
SET yksikko = tpk.yksikko
FROM toimenpidekoodi tpk
WHERE tehtava = tpk.id;

-- Lisää uusi tehtävä Liikenteen varmistaminen kelirikkokohteessa, jossa seurattava yksikkö on tonni
INSERT INTO toimenpidekoodi
(nimi,
 koodi,
 emo,
 taso,
 luotu,
 poistettu,
 yksikko,
 tuotenumero,
 jarjestys,
 hinnoittelu,
 api_seuranta,
 suoritettavatehtava)
VALUES ('Liikenteen varmistaminen kelirikkokohteessa (tonni)',
  NULL,
  (SELECT id
   FROM toimenpidekoodi
   WHERE koodi = '23124'),
  4,
  now(),
  FALSE,
  't',
  NULL,
  NULL,
  '{yksikkohintainen}',
  FALSE,
  NULL);