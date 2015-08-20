ALTER TABLE siltatarkastus DROP CONSTRAINT uniikki_silta_tarkastusaika;
CREATE UNIQUE INDEX uniikki_silta_tarkastusaika on siltatarkastus (silta, tarkastusaika) WHERE poistettu = false; 