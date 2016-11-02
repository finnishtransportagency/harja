CREATE TABLE tekniset_laitteet_urakka (
  id            SERIAL PRIMARY KEY,
  urakkanro     VARCHAR(16),
  alue          GEOMETRY
);

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('ptj', 'tekniset-laitteet-urakat-haku');

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('ptj', 'tekniset-laitteet-urakat-muutospaivamaaran-haku');
