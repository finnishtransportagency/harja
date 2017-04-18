CREATE TABLE turvalaite (
  id               SERIAL PRIMARY KEY,
  sijainti         GEOMETRY,
  turvalaitenumero INTEGER
);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('ptj', 'turvalaitteiden-haku')