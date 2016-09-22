-- Tieverkon osien ajoradat

DROP TABLE tieverkko;
DROP TABLE tieverkko_geom;

CREATE TABLE tr_osan_ajorata (
  tie INTEGER,
  osa INTEGER,
  ajorata INTEGER,
  geom GEOMETRY
);

CREATE INDEX tr_osan_ajorata_tie_osa_idx ON tr_osan_ajorata (tie,osa);

CREATE INDEX tr_osan_ajorata_geom_idx ON tr_osan_ajorata USING GIST (geom);
