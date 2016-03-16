ALTER TABLE turvallisuuspoikkeama ADD COLUMN tyontekijanammatti_muu VARCHAR(512);
UPDATE turvallisuuspoikkeama SET tyontekijanammatti_muu = turvallisuuspoikkeama.tyontekijanammatti;
ALTER TABLE turvallisuuspoikkeama DROP COLUMN tyontekijanammatti;