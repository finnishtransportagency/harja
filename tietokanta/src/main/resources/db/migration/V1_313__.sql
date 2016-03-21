-- Lisää erilliskustannus tauluun ulkoinen_id ja vaadi uniikki (luoja, ulkoinen_id) (konversioita varten)
ALTER TABLE erilliskustannus ADD COLUMN ulkoinen_id INTEGER;
ALTER TABLE erilliskustannus ADD CONSTRAINT uniikki_ulkoinen_erilliskustannus UNIQUE (ulkoinen_id, luoja);