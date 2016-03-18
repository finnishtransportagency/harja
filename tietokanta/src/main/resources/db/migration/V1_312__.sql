ALTER TABLE tarkastusreitti DROP COLUMN kuva;
ALTER TABLE tarkastusreitti DROP COLUMN mimetype;
ALTER TABLE tarkastusreitti ADD COLUMN kuva integer REFERENCES liite(id);
