CREATE TABLE vv_alus (
  mmsi INTEGER PRIMARY KEY,
  nimi TEXT,
  lisatiedot TEXT,

  luotu TIMESTAMP DEFAULT NOW(),
  luoja INTEGER REFERENCES kayttaja(id) NOT NULL,
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja(id),
  poistettu BOOLEAN DEFAULT FALSE,
  poistaja INTEGER REFERENCES kayttaja(id)
);

CREATE TABLE vv_alus_urakka (
  alus INTEGER REFERENCES vv_alus(mmsi),
  urakka INTEGER REFERENCES urakka(id),

  luotu TIMESTAMP DEFAULT NOW(),
  luoja INTEGER REFERENCES kayttaja(id) NOT NULL,
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja(id),
  poistettu BOOLEAN DEFAULT FALSE,
  poistaja INTEGER REFERENCES kayttaja(id)
);

CREATE TABLE vv_alus_sijainti (
  alus INTEGER REFERENCES vv_alus (mmsi),
  sijainti POINT NOT NULL,
  aika TIMESTAMP NOT NULL
);

-- BRIN stands for Block Range Index.
-- BRIN is designed for handling very large tables in which certain columns have some natural
-- correlation with their physical location within the table.
-- A block range is a group of pages that are physically adjacent in the table;
-- for each block range, some summary info is stored by the index.
-- For example, a table storing a store's sale orders might have
-- a date column on which each order was placed, and most of the time the entries
-- for earlier orders will appear earlier in the table as well
CREATE INDEX pvm_ja_alus ON vv_alus_sijainti USING BRIN (aika, alus);