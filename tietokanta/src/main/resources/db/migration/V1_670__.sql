CREATE TYPE kommentin_tila AS ENUM ('luotu', 'muokattu', 'poistettu', 'hyvaksytty', 'hylatty');

CREATE TABLE kan_toimenpide_kommentti (
  id SERIAL PRIMARY KEY,
  tila kommentin_tila NOT NULL,
  aika TIMESTAMP NOT NULL,
  kommentti TEXT,

  "kayttaja-id" INTEGER REFERENCES kayttaja(id) NOT NULL,
  -- Voisi ehkä olla "hinnoittelun id", mutta hinnoittelun yhteenkokoavaa taulua ei ole, vaan
  -- hinnoittelu koostuu useasta rivistä kan_hinta taulussa, jotka kaikki viittaavat toimenpiteeseen.
  "toimenpide-id" INTEGER REFERENCES kan_toimenpide(id) NOT NULL
);