ALTER TABLE kan_hairio
  ADD COLUMN kuittaaja INTEGER REFERENCES kayttaja (id) NOT NULL;