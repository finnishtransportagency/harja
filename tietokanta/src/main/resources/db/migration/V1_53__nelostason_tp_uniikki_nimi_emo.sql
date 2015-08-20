
ALTER TABLE toimenpidekoodi ADD CONSTRAINT uniikki_nimi_emo UNIQUE (nimi, emo);
