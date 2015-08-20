-- Korjaa maksuerätyypit vastaamaan toteumatyyppien arvoja


-- enum arvon lisääminen ei postgresilta onnistu: ks. workaround
-- http://stackoverflow.com/questions/1771543/postgresql-updating-an-enum-type

ALTER TYPE maksueratyyppi
RENAME TO _makty;

CREATE TYPE maksueratyyppi AS ENUM ('kokonaishintainen', 'yksikkohintainen', 'lisatyo', 'indeksi', 'bonus', 'sakko', 'akillinen-hoitotyo', 'muu');

ALTER TABLE maksuera RENAME COLUMN tyyppi TO _tyyppi;

ALTER TABLE maksuera ADD tyyppi maksueratyyppi;

UPDATE maksuera
SET tyyppi =
CASE
WHEN _tyyppi = 'akillinen_hoitotyo'
  THEN
    'akillinen-hoitotyo'
ELSE
  _tyyppi::text::maksueratyyppi
END;

ALTER TABLE maksuera DROP COLUMN _tyyppi;
DROP TYPE _makty;