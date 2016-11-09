-- Luo geometrioille uusi taulu
CREATE TABLE siltapalvelusopimus (
  id        SERIAL PRIMARY KEY,
  urakkanro VARCHAR(16),
  alue      GEOMETRY
);

-- Lisää uudet integraatiot lokiin
INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('ptj', 'siltojen-palvelusopimukset-haku');

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('ptj', 'siltojen-palvelusopimukset-muutospaivamaaran-haku');