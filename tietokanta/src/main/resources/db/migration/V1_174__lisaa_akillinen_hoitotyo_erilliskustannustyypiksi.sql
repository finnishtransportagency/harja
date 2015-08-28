ALTER TYPE erilliskustannustyyppi
RENAME TO _ekt;

CREATE TYPE erilliskustannustyyppi AS ENUM ('tilaajan_maa-aines', 'vahinkojen_korjaukset', 'asiakastyytyvaisyysbonus', 'akillinen-hoitotyo', 'muu');

ALTER TABLE erilliskustannus RENAME COLUMN tyyppi TO _tyyppi;

ALTER TABLE erilliskustannus ADD tyyppi erilliskustannustyyppi;

UPDATE erilliskustannus
SET tyyppi = _tyyppi :: TEXT :: erilliskustannustyyppi;

ALTER TABLE erilliskustannus ALTER COLUMN tyyppi SET NOT NULL;

ALTER TABLE erilliskustannus DROP COLUMN _tyyppi;
DROP TYPE _ekt;