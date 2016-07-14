CREATE TABLE suljettu_tieosuus (
  id                  SERIAL PRIMARY KEY,
  jarjestelma         VARCHAR(128) NOT NULL,
  osuus_id            INTEGER      NOT NULL,
  alkuaidan_sijainti  POINT        NOT NULL,
  loppuaidan_sijainti POINT        NOT NULL,
  vastaanotettu       TIMESTAMP DEFAULT current_timestamp,
  asetettu            TIMESTAMP,
  muokattu            TIMESTAMP,
  poistettu           TIMESTAMP,
  kaistat             INTEGER [],
  ajoradat            INTEGER [],
  yllapitokohde       INTEGER REFERENCES yllapitokohde (id),
  kirjaaja            INTEGER REFERENCES kayttaja (id),
  poistaja            INTEGER REFERENCES kayttaja (id),
  tr_tie              INTEGER,
  tr_aosa             INTEGER,
  tr_aet              INTEGER,
  tr_losa             INTEGER,
  tr_let              INTEGER,
  geometria           GEOMETRY,
  UNIQUE (jarjestelma, osuus_id)
);

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('api', 'kirjaa-suljettu-tieosuus');

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('api', 'poista-suljettu-tieosuus');