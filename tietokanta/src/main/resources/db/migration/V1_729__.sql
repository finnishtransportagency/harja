CREATE TABLE harjatila (
  id SERIAL PRIMARY KEY,
  palvelin TEXT,
  tila JSONB,
  "osa-alue" TEXT,
  paivitetty TIMESTAMP,
  UNIQUE (palvelin, "osa-alue")
);
