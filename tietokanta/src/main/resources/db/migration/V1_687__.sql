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
  luoja              INTEGER REFERENCES kayttaja (id),
  luotu              TIMESTAMP DEFAULT NOW(),
  muokkaaja          INTEGER REFERENCES kayttaja (id),
  muokattu           TIMESTAMP,
  poistettu          BOOLEAN                                 NOT NULL                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 DEFAULT FALSE,

  urakka             INTEGER REFERENCES urakka (id)          NOT NULL,
  paikkauskohde      INTEGER REFERENCES paikkauskohde (id)   NOT NULL,
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
  UNIQUE ("ulkoinen-id", luoja, urakka)
);

CREATE TABLE paikkauksen_tienkohta (
  id              SERIAL PRIMARY KEY,
  paikkaustoteuma INTEGER REFERENCES paikkaustoteuma (id),
  ajorata         INTEGER,
  reunat          INTEGER [],
  ajourat         INTEGER [],
  ajouravalit     INTEGER [],
  keskisaumat     INTEGER []
);

CREATE TABLE paikkauksen_materiaalit (
  id              SERIAL PRIMARY KEY,
  paikkaustoteuma INTEGER REFERENCES paikkaustoteuma (id),
  esiintyma       TEXT,
  "km-arvo"       TEXT,
  muotoarvo       TEXT,
  pitoisuus       DECIMAL,
  "lisa-aineet"   TEXT
);

