-- Turvallisuuspoikkeaman muutokset (HAR-1743)
-- Vanhoja turpo-tyyppejä ei voi mäpätä suoraan uusiin. Mäpätään Mikon toiveesta kaikki työtapaturmiksi.

CREATE TYPE turvallisuuspoikkeama_luokittelu AS ENUM ('tyotapaturma', 'vaaratilanne', 'turvallisuushavainto');
CREATE TYPE turvallisuuspoikkeama_vahinkoluokittelu AS ENUM ('henkilovahinko','omaisuusvahinko', 'ymparistovahinko');
CREATE TYPE turvallisuuspoikkeama_vakavuusaste AS ENUM ('vakava','lieva');

ALTER TABLE turvallisuuspoikkeama RENAME COLUMN tyyppi TO tyyppi_;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN tyyppi turvallisuuspoikkeama_luokittelu[];
ALTER TABLE turvallisuuspoikkeama ADD COLUMN vahinkoluokittelu turvallisuuspoikkeama_vahinkoluokittelu[];
ALTER TABLE turvallisuuspoikkeama ADD COLUMN vakavuusaste turvallisuuspoikkeama_vakavuusaste;
UPDATE turvallisuuspoikkeama SET tyyppi = ARRAY['tyotapaturma']::turvallisuuspoikkeama_luokittelu[];
ALTER TABLE turvallisuuspoikkeama DROP COLUMN tyyppi_;

DROP TYPE turvallisuuspoikkeamatyyppi;
