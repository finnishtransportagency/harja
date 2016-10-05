-- Migratoi turpon vahingoittuneet ruumiinosat ja vammat yhdeksi arvoksi.
-- Vanhasta datasta jää jäljelle (satummanvarainen) ensimmäinen valinta, muut tuhotaan.
ALTER TABLE turvallisuuspoikkeama RENAME vahingoittuneet_ruumiinosat TO vahingoittuneet_ruumiinosat_temp;
ALTER TABLE turvallisuuspoikkeama RENAME vammat TO vammat_temp;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN vahingoittuneet_ruumiinosat turvallisuuspoikkeama_vahingoittunut_ruumiinosa;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN vammat turvallisuuspoikkeama_aiheutuneet_vammat;
UPDATE turvallisuuspoikkeama SET vahingoittuneet_ruumiinosat = vahingoittuneet_ruumiinosat_temp[0];
UPDATE turvallisuuspoikkeama SET vammat = vammat_temp[0];

ALTER TABLE turvallisuuspoikkeama DROP COLUMN vahingoittuneet_ruumiinosat_temp;
ALTER TABLE turvallisuuspoikkeama DROP COLUMN vammat_temp;

-- Turvallisuuspoikkeamalla turi-id
ALTER TABLE turvallisuuspoikkeama ADD COLUMN turi_id INTEGER;