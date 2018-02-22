ALTER TABLE paikkaustoteuma
  RENAME TO paikkaus;

ALTER TABLE paikkauksen_tienkohta
  RENAME COLUMN "paikkaustoteuma-id" TO "paikkaus-id";

ALTER TABLE paikkauksen_materiaali
  RENAME COLUMN "paikkaustoteuma-id" TO "paikkaus-id";

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
  maara              DECIMAL,

  CONSTRAINT paikkauskustannuksen_uniikki_ulkoinen_id_luoja_urakka
  UNIQUE ("ulkoinen-id", "luoja-id", "urakka-id")
);

UPDATE integraatio
SET nimi = 'kirjaa-paikkaus'
WHERE nimi = 'kirjaa-paikkaustoteuma';