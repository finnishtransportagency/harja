<<<<<<< HEAD
ALTER TABLE turvallisuuspoikkeama
ADD COLUMN lahetetty TIMESTAMP,
ADD COLUMN lahetys_onnistunut BOOLEAN;

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('turi', 'laheta-turvallisuuspoikkeama');
=======
ALTER TABLE laatupoikkeama ADD CONSTRAINT uniikki_ulkoinen_laatupoikkeama UNIQUE (ulkoinen_id, luoja);
>>>>>>> develop
