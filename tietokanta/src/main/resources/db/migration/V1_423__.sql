-- Tieverkon osien ajoradat

CREATE TABLE tr_osan_ajorata (
  tie INTEGER,
  osa INTEGER,
  oikea GEOMETRY,
  vasen GEOMETRY
);

CREATE INDEX tr_osan_ajorata_tie_osa_idx ON tr_osan_ajorata (tie,osa);
