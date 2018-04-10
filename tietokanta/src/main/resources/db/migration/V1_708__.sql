-- on jo olemassa, mutta nämä on ne arvot
-- CREATE TYPE kommentin_tila AS ENUM ('luotu', 'muokattu', 'poistettu', 'hyvaksytty', 'hylatty');

CREATE TABLE vv_hinnoittelu_kommentti (
  id SERIAL PRIMARY KEY,
  tila kommentin_tila NOT NULL,
  aika TIMESTAMP NOT NULL,
  kommentti TEXT,
  "laskutus-pvm" TIMESTAMP CHECK ((tila = 'hyvaksytty' AND "laskutus-pvm" IS NOT NULL) OR
                                  (tila != 'hyvaksytty' AND "laskutus-pvm" IS NULL)),

  "kayttaja-id" INTEGER REFERENCES kayttaja(id) NOT NULL,
  "hinnoittelu-id" INTEGER REFERENCES vv_hinnoittelu(id) NOT NULL
);
