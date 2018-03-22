-- Dropataan uudelleennimeämisten takia migraatiossa 682 luodut taulut
DROP TABLE paikkauksen_materiaali;
DROP TABLE paikkauksen_tienkohta;
DROP TABLE paikkaustoteuma;

CREATE TABLE paikkaus (
  id                 SERIAL PRIMARY KEY,
  "luoja-id"         INTEGER REFERENCES kayttaja (id),
  luotu              TIMESTAMP DEFAULT NOW(),
  "muokkaaja-id"     INTEGER REFERENCES kayttaja (id),
  muokattu           TIMESTAMP,
  "poistaja-id"      INTEGER REFERENCES kayttaja (id),
  poistettu          BOOLEAN                                     NOT NULL                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            DEFAULT FALSE,

  "urakka-id"        INTEGER REFERENCES urakka (id)              NOT NULL,
  "paikkauskohde-id" INTEGER REFERENCES paikkauskohde (id)       NOT NULL,
  "ulkoinen-id"      INTEGER                                     NOT NULL,

  alkuaika           TIMESTAMP                                   NOT NULL,
  loppuaika          TIMESTAMP                                   NOT NULL,

  tierekisteriosoite TR_OSOITE                                   NOT NULL,

  tyomenetelma       TEXT                                        NOT NULL,
  massatyyppi        TEXT                                        NOT NULL,
  leveys             DECIMAL,
  massamenekki       INTEGER,
  raekoko            INTEGER,
  kuulamylly         TEXT,

  CONSTRAINT paikkauksen_uniikki_ulkoinen_id_luoja_urakka
  UNIQUE ("ulkoinen-id", "luoja-id", "urakka-id")
);

CREATE TABLE paikkauksen_tienkohta (
  id            SERIAL PRIMARY KEY,
  "paikkaus-id" INTEGER REFERENCES paikkaus (id),
  ajorata       INTEGER,
  reunat        INTEGER [],
  ajourat       INTEGER [],
  ajouravalit   INTEGER [],
  keskisaumat   INTEGER []
);

CREATE TABLE paikkauksen_materiaali (
  id                SERIAL PRIMARY KEY,
  "paikkaus-id"     INTEGER REFERENCES paikkaus(id),
  esiintyma         TEXT,
  "kuulamylly-arvo" TEXT,
  muotoarvo         TEXT,
  sideainetyyppi    TEXT,
  pitoisuus         DECIMAL,
  "lisa-aineet"     TEXT
);

CREATE TYPE PAIKKAUSTOTEUMATYYPPI AS ENUM ('kokonaishintainen', 'yksikkohintainen');

CREATE TABLE paikkaustoteuma (
  id                 SERIAL PRIMARY KEY,
  "ulkoinen-id"      INTEGER                                     NOT NULL,

  "urakka-id"        INTEGER REFERENCES urakka (id)              NOT NULL,
  "paikkauskohde-id" INTEGER REFERENCES paikkauskohde (id)       NOT NULL,
  "toteuma-id"       INTEGER REFERENCES toteuma,

  "luoja-id"         INTEGER REFERENCES kayttaja (id),
  luotu              TIMESTAMP DEFAULT NOW(),
  "muokkaaja-id"     INTEGER REFERENCES kayttaja (id),
  muokattu           TIMESTAMP,
  "poistaja-id"      INTEGER REFERENCES kayttaja (id),
  poistettu          BOOLEAN,

  kirjattu           TIMESTAMP DEFAULT NOW(),

  tyyppi             PAIKKAUSTOTEUMATYYPPI,

  -- kokonaishintaisen kustannuksen kentät
  selite             TEXT,
  hinta              DECIMAL,

  -- yksikköhintaisen kustannuksen kentät
  yksikko            TEXT,
  yksikkohinta       DECIMAL,
  maara              DECIMAL
);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'kirjaa-paikkaus');
