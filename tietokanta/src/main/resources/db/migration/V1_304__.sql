-- Lisää päivystys tauluun yksilöintivaatimus
ALTER TABLE paivystys ADD COLUMN luoja INTEGER;
ALTER TABLE paivystys ADD CONSTRAINT uniikki_paivystys UNIQUE (ulkoinen_id, luoja);