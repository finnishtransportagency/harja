-- Havainnolle kuvaus kenttä
ALTER TABLE havainto ADD COLUMN kuvaus VARCHAR(4096),
ADD COLUMN tr_numero INTEGER,
ADD COLUMN tr_alkuosa INTEGER,
ADD COLUMN tr_loppuosa INTEGER,
ADD COLUMN tr_loppuetaisyys INTEGER,
ADD COLUMN sijainti POINT;

-- Havainnoille ja liitteille linkkitaulu
CREATE TABLE havainto_liite (
  havainto INTEGER REFERENCES havainto (id),
  liite    INTEGER REFERENCES liite (id)
);
