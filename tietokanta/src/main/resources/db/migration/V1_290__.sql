-- Kuvaus: Lisää varustetoteumaan tarkastusaika, lisää tarkastus varustetoteuman tyypiksi
ALTER TYPE varustetoteuma_tyyppi RENAME TO _vtt;

CREATE TYPE varustetoteuma_tyyppi AS ENUM ('lisatty','paivitetty','poistettu','tarkastus');

ALTER TABLE varustetoteuma RENAME COLUMN toimenpide TO _toimenpide;

ALTER TABLE varustetoteuma ADD toimenpide varustetoteuma_tyyppi;

UPDATE varustetoteuma
SET toimenpide = _toimenpide :: TEXT :: varustetoteuma_tyyppi;

ALTER TABLE varustetoteuma DROP COLUMN _toimenpide;
DROP TYPE _vtt;



ALTER TABLE varustetoteuma ADD COLUMN tarkastusaika TIMESTAMP;
