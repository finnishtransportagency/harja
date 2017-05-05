-- VÄYLÄT

CREATE TYPE vv_vaylatyyppi AS ENUM ('kauppamerenkulku', 'muu');

CREATE TABLE vv_vayla (
  id SERIAL PRIMARY KEY,
  nimi VARCHAR NOT NULL,
  "vatu-id" INTEGER NOT NULL,
  sijainti GEOMETRY,
  tyyppi vv_vaylatyyppi NOT NULL
);

-- TURVALAITTEET

CREATE TYPE turvalaitteen_tyyppi AS ENUM ('viitta', 'kiintea', 'poiju');

ALTER TABLE turvalaite RENAME TO vv_turvalaite;

ALTER TABLE vv_turvalaite
  ADD COLUMN tyyppi turvalaitteen_tyyppi,
  ADD COLUMN vayla INTEGER REFERENCES vv_vayla(id);

-- VIKAILMOITUKSET

CREATE TABLE vv_vikailmoitus (
  id SERIAL PRIMARY KEY,
  "reimari-id" INTEGER,
  kuvaus VARCHAR,
  pvm DATE,
  "turvalaite-id" INTEGER REFERENCES vv_turvalaite(id) NOT NULL,
  "toteuma-id" INTEGER REFERENCES toteuma(id)
);

-- HINNOITTELU

CREATE TYPE vv_hintatyyppi AS ENUM ('komponentti', 'materiaali', 'matka', 'tyo', 'muu');

CREATE TABLE vv_hinta (
  id SERIAL PRIMARY KEY,
  "toteuma-id" INTEGER REFERENCES toteuma(id) NOT NULL,
  hinta NUMERIC NOT NULL,
  kuvaus VARCHAR,
  yleiskustannuslisa BOOLEAN DEFAULT FALSE NOT NULL,
  tyyppi vv_hintatyyppi
);