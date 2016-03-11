-- Lisää organisaatio tauluun ulkoinen_id (konversioita varten)
ALTER TABLE organisaatio ADD COLUMN luoja INTEGER;
ALTER TABLE organisaatio ADD COLUMN ulkoinen_id INTEGER;
ALTER TABLE organisaatio ADD CONSTRAINT uniikki_ulkoinen_organisaatio UNIQUE (ulkoinen_id, luoja);