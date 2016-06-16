-- Siltatarkastuksen kohteelle liite
CREATE TABLE siltatarkastus_kohde_liite (
  siltatarkastus INTEGER REFERENCES siltatarkastus (id),
  kohde smallint, -- tarkastuskohteen numero, esim. 12 Liikuntasaumalaitteiden siisteys,
  liite INTEGER REFERENCES liite (id)
);
COMMENT ON TABLE tarkastus_liite IS 'Siltatarkastuksen kohteeseen liittyvät liitteet';