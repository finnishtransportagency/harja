CREATE TABLE kayttoseuranta(
  id SERIAL PRIMARY KEY,
  kayttaja INTEGER REFERENCES kayttaja (id) NOT NULL,
  aika TIMESTAMP NOT NULL,
  tila BOOLEAN NOT NULL,
  sivu TEXT NOT NULL,
  lisatieto TEXT);