CREATE TABLE jarjestelman_tila (
  id SERIAL PRIMARY KEY,
  palvelin TEXT,
  tila JSONB,
  "osa-alue" TEXT,
  paivitetty TIMESTAMP,
  UNIQUE (palvelin, "osa-alue")
);