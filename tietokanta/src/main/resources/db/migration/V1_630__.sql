ALTER TABLE kan_kohde_urakka
  ADD COLUMN muokattu TIMESTAMP,
  ADD COLUMN muokkaaja INTEGER REFERENCES kayttaja (id);