<<<<<<< HEAD
-- Varustetoteumalle tieto siitä onko lähetetty tierekisteriin
ALTER TABLE varustetoteuma ADD COLUMN lahetetty_tierekisteriin BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE varustetoteuma ADD COLUMN sijainti geometry;
=======
DROP MATERIALIZED VIEW tieverkko_paloina;

CREATE TABLE tieverkko_geom (
  tie INTEGER NOT NULL,
  geom geometry NOT NULL,
  suunta BIT NOT NULL,
  PRIMARY KEY (tie, suunta)
);

CREATE INDEX tieverkko_geom_idx ON tieverkko_geom USING GIST (geom);

CREATE TABLE tr_osien_pituudet (
  tie INTEGER NOT NULL,
  osa INTEGER NOT NULL,
  pituus INTEGER NOT NULL,
  PRIMARY KEY (tie,osa)
);
>>>>>>> develop
