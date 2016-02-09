-- Turvallisuuspoikkeaman muutokset (HAR-1743)

CREATE TYPE turvallisuuspoikkeama_vahinkoluokittelu AS ENUM ('henkilovahinko','omaisuusvahinko', 'ymparistovahinko');
CREATE TYPE turvallisuuspoikkeama_vakavuusaste AS ENUM ('vakava','lievä');

ALTER TABLE turvallisuuspoikkeama ADD COLUMN vahinkoluokittelu turvallisuuspoikkeama_vahinkoluokittelu;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN vakavuusaste turvallisuuspoikkeama_vakavuusaste;