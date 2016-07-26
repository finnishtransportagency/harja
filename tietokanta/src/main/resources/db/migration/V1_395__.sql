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
