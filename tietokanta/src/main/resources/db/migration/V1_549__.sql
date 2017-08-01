CREATE TABLE tr_ajoratojen_pituudet (
  tie     INTEGER NOT NULL,
  osa     INTEGER NOT NULL,
  ajorata INTEGER NOT NULL CHECK (ajorata >= 0 AND ajorata <= 2),
  pituus  INTEGER NOT NULL,
  PRIMARY KEY (tie, osa, ajorata)
);
