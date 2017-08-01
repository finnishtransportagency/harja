CREATE TYPE yllapito_muu_toteuma_tyyppi AS ENUM ('muu', 'arvonmuutos', 'indeksi');

ALTER TABLE yllapito_muu_toteuma
 ADD COLUMN tyyppi yllapito_muu_toteuma_tyyppi NOT NULL DEFAULT 'muu';