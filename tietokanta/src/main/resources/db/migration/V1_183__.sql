ALTER TABLE turvallisuuspoikkeama
ADD COLUMN ulkoinen_id INT;

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'lisaa-turvallisuuspoikkeama');