-- Uusi turpotyyppi: muu
ALTER TYPE turvallisuuspoikkeama_luokittelu RENAME TO turvallisuuspoikkeama_luokittelu_;
CREATE TYPE turvallisuuspoikkeama_luokittelu AS ENUM ('tyotapaturma', 'vaaratilanne', 'turvallisuushavainto', 'muu');

ALTER TABLE turvallisuuspoikkeama RENAME COLUMN tyyppi TO tyyppi_;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN tyyppi turvallisuuspoikkeama_luokittelu[];
UPDATE turvallisuuspoikkeama SET tyyppi = tyyppi_ :: text ::turvallisuuspoikkeama_luokittelu[];

ALTER TABLE turvallisuuspoikkeama DROP COLUMN tyyppi_;
DROP TYPE turvallisuuspoikkeama_luokittelu_;
