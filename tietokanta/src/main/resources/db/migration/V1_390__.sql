CREATE TABLE suljettu_tieosuus (
  id                 SERIAL PRIMARY KEY,
  jarjestelma        VARCHAR(128) NOT NULL,
  osuus_id           INTEGER      NOT NULL,
  alkuaidan_sijainti POINT        NOT NULL,
  loppaidan_sijainti POINT        NOT NULL,
  vastaanotettu      TIMESTAMP DEFAULT current_timestamp,
  asetettu           TIMESTAMP,
  muokattu           TIMESTAMP,
  poistettu          TIMESTAMP,
  kaistat            INTEGER [],
  ajoradat           INTEGER [],
  yllapitokohde      INTEGER REFERENCES yllapitokohde (id),
  kirjaaja           INTEGER REFERENCES kayttaja (id),
  UNIQUE (jarjestelma, osuus_id)
);

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('api', 'kirjaa-suljettu-tieosuus');

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('api', 'poista-suljettu-tieosuus');