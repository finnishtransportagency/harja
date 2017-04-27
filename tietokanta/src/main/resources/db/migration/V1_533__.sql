CREATE TABLE turvalaite (
  id       SERIAL PRIMARY KEY,
  sijainti GEOMETRY,
  tunniste VARCHAR(128),
  arvot    JSONB);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('ptj', 'turvalaitteiden-haku');
