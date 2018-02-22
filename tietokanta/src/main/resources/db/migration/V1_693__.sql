ALTER TABLE paikkaustoteuma
  RENAME TO paikkaus;

CREATE TABLE paikkauskustannus (
  id                 SERIAL PRIMARY KEY,
  "ulkoinen-id"      INTEGER                                     NOT NULL,

  "urakka-id"        INTEGER REFERENCES urakka (id)              NOT NULL,
  "paikkauskohde-id" INTEGER REFERENCES paikkauskohde (id)       NOT NULL,

  "luoja-id"         INTEGER REFERENCES kayttaja (id),
  luotu              TIMESTAMP DEFAULT NOW(),
  "muokkaaja-id"     INTEGER REFERENCES kayttaja (id),
  muokattu           TIMESTAMP,
  "poistaja-id"      INTEGER REFERENCES kayttaja (id),
  poistettu          BOOLEAN,

  kirjattu           TIMESTAMP DEFAULT NOW(),

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