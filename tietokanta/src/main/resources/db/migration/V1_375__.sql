-- Siltatarkastuksen kohteelle liite
CREATE TABLE siltatarkastuskohde_liite (
  siltatarkastuskohde INTEGER REFERENCES siltatarkastuskohde (id),
  liite INTEGER REFERENCES liite (id)
);
COMMENT ON TABLE tarkastus_liite IS 'Siltatarkastuksen kohteeseen liittyvät liitteet';