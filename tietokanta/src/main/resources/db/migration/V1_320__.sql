-- Turpon lisämuutokset

UPDATE turvallisuuspoikkeama SET vaylamuoto = 'tie'::vaylamuoto WHERE vaylamuoto IS NULL;


ALTER TABLE turvallisuuspoikkeama ADD COLUMN toteuttaja VARCHAR(1024);
ALTER TABLE turvallisuuspoikkeama ADD COLUMN tilaaja VARCHAR(1024);
ALTER TABLE turvallisuuspoikkeama ADD COLUMN turvallisuuskoordinaattori_etunimi VARCHAR(1024);
ALTER TABLE turvallisuuspoikkeama ADD COLUMN turvallisuuskoordinaattori_sukunimi VARCHAR(1024);
ALTER TABLE turvallisuuspoikkeama ADD COLUMN laatika_etunimi VARCHAR(1024);
ALTER TABLE turvallisuuspoikkeama ADD COLUMN laatija_sukunimi VARCHAR(1024);