ALTER TABLE tarkastus
  DROP CONSTRAINT tarkastus_yllapitokohde_fkey,
  ADD CONSTRAINT tarkastus_yllapitokohde_fkey
FOREIGN KEY (yllapitokohde) REFERENCES yllapitokohde (id)
ON DELETE SET NULL;

CREATE INDEX tarkastus_yllapitokohde_index ON tarkastus (yllapitokohde);