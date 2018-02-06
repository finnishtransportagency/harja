CREATE TABLE paikkauskohde (
  id            SERIAL PRIMARY KEY,
  luoja         INTEGER REFERENCES kayttaja (id),
  "ulkoinen-id" INTEGER NOT NULL,
  "nimi"        TEXT    NOT NULL,
  CONSTRAINT paikkauskohteen_uniikki_ulkoinen_id_luoja
  UNIQUE ("ulkoinen-id", luoja)
);

CREATE TABLE paikkaustoteuma (
  id                 SERIAL PRIMARY KEY,
  "luoja-id"     INTEGER REFERENCES kayttaja (id),
  luotu          TIMESTAMP                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             DEFAULT NOW(),
  "muokkaaja-id" INTEGER REFERENCES kayttaja (id),
  muokattu       TIMESTAMP,
  "poistaja-id"  INTEGER REFERENCES kayttaja (id),
  poistettu      BOOLEAN                                     NOT NULL                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            DEFAULT FALSE,

  "urakka-id"    INTEGER REFERENCES urakka (id)              NOT NULL,
  "paikkauskohde-id" INTEGER REFERENCES paikkauskohde (id)   NOT NULL,
  "ulkoinen-id"      INTEGER                                 NOT NULL,

  alkuaika           TIMESTAMP                               NOT NULL,
  loppuaika          TIMESTAMP                               NOT NULL,

  tierekisteriosoite TR_OSOITE                               NOT NULL,

  tyomenetelma       TEXT                                    NOT NULL,
  massatyyppi        TEXT                                    NOT NULL,
  leveys             DECIMAL,
  massamenekki       INTEGER,
  raekoko            INTEGER,
  kuulamylly         TEXT,

  CONSTRAINT paikkaustoteuman_uniikki_ulkoinen_id_luoja_urakka
  UNIQUE ("ulkoinen-id", "luoja-id", "urakka-id")
);

CREATE TABLE paikkauksen_tienkohta (
  id                   SERIAL PRIMARY KEY,
  "paikkaustoteuma-id" INTEGER REFERENCES paikkaustoteuma (id),
  ajorata              INTEGER,
  reunat               INTEGER [],
  ajourat              INTEGER [],
  ajouravalit          INTEGER [],
  keskisaumat          INTEGER []
);

CREATE TABLE paikkauksen_materiaalit (
  id                   SERIAL PRIMARY KEY,
  "paikkaustoteuma-id" INTEGER REFERENCES paikkaustoteuma (id),
  esiintyma            TEXT,
  "km-arvo"            TEXT,
  muotoarvo            TEXT,
  sideainetyyppi       TEXT,
  pitoisuus            DECIMAL,
  "lisa-aineet"        TEXT
);

