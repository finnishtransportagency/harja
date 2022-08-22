ALTER TABLE tarkastus_liite DROP CONSTRAINT tarkastus_liite_liite_fkey;
ALTER TABLE tarkastus_liite ADD FOREIGN KEY (liite) REFERENCES liite(id) ON DELETE CASCADE;