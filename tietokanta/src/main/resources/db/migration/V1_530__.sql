CREATE TABLE turvalaite (
  id               SERIAL PRIMARY KEY,
  sijainti         GEOMETRY,
  tunniste         VARCHAR(128),
  nimi             VARCHAR(254),
  alityyppi        VARCHAR(64),
  sijainnin_kuvaus VARCHAR(254),
  vayla            VARCHAR(32),
  tila             VARCHAR(32));

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('ptj', 'turvalaitteiden-haku');