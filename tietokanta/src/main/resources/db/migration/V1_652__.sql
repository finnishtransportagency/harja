ALTER TABLE vv_materiaali
  ADD COLUMN hairiotilanne INTEGER REFERENCES vv_hairio (id);