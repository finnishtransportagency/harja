ALTER TABLE turvallisuuspoikkeama
ADD COLUMN lahetetty TIMESTAMP,
ADD COLUMN lahetys_onnistunut BOOLEAN;

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('turi', 'laheta-turvallisuuspoikkeama');