-- Kuvaus: Lisää varustetoteumaan tarkastusaika, lisää tarkastus varustetoteuman tyypiksi
ALTER TYPE varustetoteuma_tyyppi RENAME TO _vtt;

CREATE TYPE varustetoteuma_tyyppi AS ENUM ('lisatty','paivitetty','poistettu','tarkastus','korjaus','puhdistus');

-- Vanha varustetoteuma-taulu
ALTER TABLE varustetoteuma RENAME COLUMN toimenpide TO _toimenpide;

ALTER TABLE varustetoteuma ADD toimenpide varustetoteuma_tyyppi;

UPDATE varustetoteuma
SET toimenpide = _toimenpide :: TEXT :: varustetoteuma_tyyppi;

-- Uusi varustetoteuma-taulu
ALTER TABLE varustetoteuma_ulkoiset RENAME COLUMN toteuma TO _toteuma;

ALTER TABLE varustetoteuma_ulkoiset ADD toteuma varustetoteuma_tyyppi;

UPDATE varustetoteuma_ulkoiset
SET toteuma = _toteuma :: TEXT :: varustetoteuma_tyyppi;


ALTER TABLE varustetoteuma_ulkoiset DROP COLUMN _toteuma;
ALTER TABLE varustetoteuma DROP COLUMN _toimenpide;
DROP TYPE _vtt;
