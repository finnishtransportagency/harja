-- Päivitetään erilliskustannustyyppi, poistetaan 'tilaajan_maa-aines', 'vahinkojen_korjaukset', 'akillinen-hoitotyo'
DELETE FROM erilliskustannus WHERE tyyppi IN ('tilaajan_maa-aines', 'vahinkojen_korjaukset', 'akillinen-hoitotyo');

ALTER TYPE erilliskustannustyyppi
RENAME TO _ekt;

CREATE TYPE erilliskustannustyyppi AS ENUM ('asiakastyytyvaisyysbonus', 'muu');

ALTER TABLE erilliskustannus RENAME COLUMN tyyppi TO _tyyppi;

ALTER TABLE erilliskustannus ADD tyyppi erilliskustannustyyppi;

UPDATE erilliskustannus
SET tyyppi = _tyyppi :: TEXT :: erilliskustannustyyppi;

ALTER TABLE erilliskustannus ALTER COLUMN tyyppi SET NOT NULL;

ALTER TABLE erilliskustannus DROP COLUMN _tyyppi;
DROP TYPE _ekt;


-- Päivitetään toteumatyyppi, lisätään vahinkojen korjaukset siinne uutena tyyppinä
UPDATE toteuma SET tyyppi = 'yksikkohintainen' WHERE tyyppi IS NULL;

ALTER TYPE toteumatyyppi
RENAME TO _tt;

CREATE TYPE toteumatyyppi AS ENUM ('kokonaishintainen', 'yksikkohintainen', 'akillinen-hoitotyo', 'lisatyo', 'muutostyo', 'vahinkojen-korjaukset', 'materiaali');

ALTER TABLE toteuma RENAME COLUMN tyyppi TO _tyyppi;

ALTER TABLE toteuma ADD tyyppi toteumatyyppi NOT NULL;

UPDATE toteuma
SET tyyppi = _tyyppi :: TEXT :: toteumatyyppi;

ALTER TABLE toteuma DROP COLUMN _tyyppi;
DROP TYPE _tt;