ALTER TABLE vv_materiaali
  ADD COLUMN hairiotilanne INTEGER REFERENCES kan_hairio (id);
