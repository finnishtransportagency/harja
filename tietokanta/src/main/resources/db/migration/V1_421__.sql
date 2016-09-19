CREATE TABLE valaistusurakka (
  id            SERIAL PRIMARY KEY,
  alueurakkanro VARCHAR(16),
  alue          GEOMETRY
);

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('ptj', 'valaistusurakat-haku');

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('ptj', 'valaistusurakat-muutospaivamaaran-haku');
