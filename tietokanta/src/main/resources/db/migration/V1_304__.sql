-- Lisää päivystys tauluun yksilöintivaatimus
ALTER TABLE paivystys ADD COLUMN luoja INTEGER;
ALTER TABLE paivystys ADD CONSTRAINT uniikki_paivystys UNIQUE (ulkoinen_id, luoja);

-- Lisää yhteyshenkilo tauluun yksilöintivaatimus
ALTER TABLE yhteyshenkilo ADD COLUMN luoja INTEGER;
ALTER TABLE yhteyshenkilo ADD CONSTRAINT uniikki_yhteyshenkilo UNIQUE (ulkoinen_id, luoja);