ALTER TABLE sanktio ADD COLUMN id serial PRIMARY KEY;
ALTER TABLE sanktio ADD COLUMN havainto integer REFERENCES havainto (id);
