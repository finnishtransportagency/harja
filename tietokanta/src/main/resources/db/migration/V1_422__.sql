CREATE TABLE paallystyspalvelusopimus (
  id            SERIAL PRIMARY KEY,
  alueurakkanro VARCHAR(16),
  alue          GEOMETRY
);

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('ptj', 'paallystyspalvelusopimukset-haku');

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('ptj', 'paallystyspalvelusopimukset-muutospaivamaaran-haku');
