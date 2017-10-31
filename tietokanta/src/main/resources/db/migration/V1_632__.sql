-- Häiriötilanteet

CREATE TYPE kan_hairio_korjauksen_tila AS ENUM ('kesken', 'valmis');

CREATE TABLE kan_hairio {
  id serial PRIMARY KEY,
  urakka integer references urakka (id),
  pvm DATE NOT NULL,
  kohde,
  vikaluokka, -- TODO PDF?
  syy,
  odotusaika,
  ammatil-lkm, -- TODO Mistä tämä on lyhenne?
  huvil-lkm, -- TODO Mistä tämä on lyhenne?
  korjaustoimenpide,
  korjausaika,
  korjauksen_tila,
  paikallinen_kaytto,
}

-- TODO DOC