-- Nimi: Varustetoteuma-taulun lisääminen
-- Kuvaus: ks. nimi

CREATE TYPE varustetoteuma_tyyppi AS ENUM ('lisatty','paivitetty','poistettu');

CREATE TABLE varustetoteuma (
  id serial PRIMARY KEY,
  tunniste varchar(128),
  toteuma integer references toteuma (id),
  toimenpide varustetoteuma_tyyppi,
  tietolaji varchar(128),
  ominaisuudet varchar(4096),
  tr_numero integer,
  tr_alkuosa integer,
  tr_loppuosa integer,
  tr_loppuetaisyys integer,
  tr_alkuetaisyys integer,
  piiri integer,
  kuntoluokka integer
);

-- Lisää vielä puuttuvat tiedot
ALTER TABLE varustetoteuma ADD COLUMN luoja integer REFERENCES kayttaja (id);
ALTER TABLE varustetoteuma ADD COLUMN luotu timestamp DEFAULT current_timestamp;