-- on jo olemassa, mutta nämä on ne arvot
-- CREATE TYPE kommentin_tila AS ENUM ('luotu', 'muokattu', 'poistettu', 'hyvaksytty', 'hylatty');

CREATE TABLE vv_hinnoittelu_kommentti (
  id SERIAL PRIMARY KEY,
  tila kommentin_tila NOT NULL,
  aika TIMESTAMP NOT NULL,
  kommentti TEXT,

  "kayttaja-id" INTEGER REFERENCES kayttaja(id) NOT NULL,
  "hinnoittelu-id" INTEGER REFERENCES vv_hinnoittelu(id) NOT NULL
);