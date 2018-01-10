ALTER TABLE vv_materiaali
  ADD COLUMN hairiotilanne INTEGER REFERENCES kan_hairio (id);

ALTER TABLE kan_hairio ALTER COLUMN pvm TYPE timestamp;
ALTER TABLE kan_hairio RENAME COLUMN pvm TO havaintoaika;

ALTER TYPE vv_materiaali_muutos ADD ATTRIBUTE hairiotilanne INTEGER;