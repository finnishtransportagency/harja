-- Häiriötilanteet

CREATE TYPE kan_hairio_korjauksen_tila AS ENUM ('kesken', 'valmis');
CREATE TYPE kan_hairio_vikaluokka AS ENUM ('sahkotekninen_vika', 'konetekninen_vika', 'liikennevaurio');

CREATE TABLE kan_hairio (
  id serial PRIMARY KEY,
  urakka INTEGER REFERENCES urakka (id) NOT NULL,
  sopimus INTEGER REFERENCES sopimus (id) NOT NULL,
  pvm DATE NOT NULL,
  kohde INTEGER REFERENCES kan_kohde (id) NOT NULL,
  vikaluokka kan_hairio_vikaluokka,
  syy VARCHAR(512) NOT NULL,
  odotusaika_h INTEGER,
  ammattiliikenne_lkm INTEGER,
  huviliikenne_lkm INTEGER,
  korjaustoimenpide VARCHAR(512),
  korjausaika_h INTEGER,
  korjauksen_tila kan_hairio_korjauksen_tila,
  paikallinen_kaytto BOOLEAN NOT NULL DEFAULT FALSE,

  -- Muokkausmetatiedot
  luoja integer REFERENCES kayttaja (id),
  luotu timestamp,
  muokkaaja integer REFERENCES kayttaja (id),
  muokattu timestamp,
  poistettu boolean NOT NULL DEFAULT FALSE
);