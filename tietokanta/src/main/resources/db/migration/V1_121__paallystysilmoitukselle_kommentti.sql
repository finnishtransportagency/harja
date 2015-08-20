ALTER TABLE paallystysilmoitus ADD COLUMN id serial PRIMARY KEY;

CREATE TABLE paallystysilmoitus_kommentti (
  havainto integer REFERENCES paallystysilmoitus (id),
  kommentti integer REFERENCES kommentti (id)
);