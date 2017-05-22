CREATE TABLE vv_hinnoittelu
(
  id        SERIAL PRIMARY KEY,
  emo       INTEGER REFERENCES vv_hinnoittelu (id),
  nimi      VARCHAR,

  muokkaaja INTEGER REFERENCES kayttaja (id),
  muokattu  TIMESTAMP,
  luoja     INTEGER REFERENCES kayttaja (id) NOT NULL,
  luotu     TIMESTAMP                        NOT NULL,
  poistettu BOOLEAN                          NOT NULL DEFAULT FALSE,
  poistaja  INTEGER REFERENCES kayttaja (id)
);

ALTER TABLE reimari_toimenpide
  ADD COLUMN "hinnoittelu-id" INTEGER REFERENCES vv_hinnoittelu (id);

CREATE TABLE vv_hinta
(
  id                 SERIAL PRIMARY KEY,
  "hinnoittelu-id"   INTEGER REFERENCES vv_hinnoittelu (id),
  otsikko            VARCHAR                          NOT NULL,
  arvo               NUMERIC                          NOT NULL,
  yleiskustannuslisa NUMERIC                          NOT NULL DEFAULT 0,

  muokkaaja          INTEGER REFERENCES kayttaja (id),
  muokattu           TIMESTAMP,
  luoja              INTEGER REFERENCES kayttaja (id) NOT NULL,
  luotu              TIMESTAMP                        NOT NULL,
  poistettu          BOOLEAN                          NOT NULL DEFAULT FALSE,
  poistaja           INTEGER REFERENCES kayttaja (id)
);