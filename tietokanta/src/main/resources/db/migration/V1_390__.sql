CREATE TABLE liikenneohjausaidat (
  id            INTEGER PRIMARY KEY,
  jarjestelma   VARCHAR(128) NOT NULL,
  aita_id       INTEGER      NOT NULL,
  sijainti      POINT        NOT NULL,
  vastaanotettu TIMESTAMP DEFAULT current_timestamp,
  asetettu      TIMESTAMP,
  muokattu      TIMESTAMP,
  poistettu     TIMESTAMP,
  kaista        INTEGER,
  ajorata       INTEGER,
  yllapitokohde INTEGER REFERENCES yllapitokohde (id),
  UNIQUE (jarjestelma, aita_id)
);
