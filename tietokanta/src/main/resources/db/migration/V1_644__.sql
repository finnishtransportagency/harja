CREATE TABLE reimari_turvalaiteryhma (
  tunnus   INTEGER PRIMARY KEY,
  nimi          TEXT,
  kuvaus          TEXT,
  turvalaitteet INTEGER []
);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('reimari', 'turvalaiteryhmat-haku');