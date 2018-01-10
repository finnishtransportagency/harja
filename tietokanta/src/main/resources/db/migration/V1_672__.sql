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

-- Otetaan toimenpiteen uusimman kommentin tila ja toimenpide-id, ja palautetaan ne toimenpide-id:t,
-- joiden tila on hyvaksytty.
CREATE VIEW kan_laskutettavat_hinnoittelut
  AS
    SELECT "toimenpide-id" FROM
      (SELECT DISTINCT ON ("toimenpide-id")
         "toimenpide-id",
         tila
       FROM kan_toimenpide_kommentti
       ORDER BY "toimenpide-id", aika DESC) AS tilat
    WHERE tila = 'hyvaksytty';