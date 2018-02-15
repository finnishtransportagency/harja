CREATE TABLE paikkauksen_kustannus (
  id                 SERIAL PRIMARY KEY,
  "paikkauskohde-id" INTEGER REFERENCES paikkauskohde (id) NOT NULL,
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
  maara              DECIMAL
);

