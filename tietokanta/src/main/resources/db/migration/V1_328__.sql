-- Tarkastusreitille havainnot-sarake takaisin ja uniikki constaintti
ALTER TABLE tarkastusreitti ADD COLUMN havainnot integer[];
ALTER TABLE tarkastusreitti ADD CONSTRAINT uniikki_tarkastusreitti UNIQUE (id, tarkastusajo);