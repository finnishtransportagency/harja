-- Päivitetään ENUM erilliskustannustyyppi :ä lisäämällä sinne uusia bonuksia
--  'alihankintabonus'
--  'lupausbonus'
--  'tktt-bonus'
-- 'tavoitepalkkio'

ALTER TYPE erilliskustannustyyppi RENAME TO _ekt;

CREATE TYPE erilliskustannustyyppi AS ENUM ('asiakastyytyvaisyysbonus','alihankintabonus', 'lupausbonus','tktt-bonus', 'tavoitepalkkio', 'muu');

ALTER TABLE erilliskustannus RENAME COLUMN tyyppi TO _tyyppi;

ALTER TABLE erilliskustannus ADD tyyppi erilliskustannustyyppi;

UPDATE erilliskustannus SET tyyppi = _tyyppi :: TEXT :: erilliskustannustyyppi;

ALTER TABLE erilliskustannus DROP COLUMN _tyyppi;
DROP TYPE _ekt;