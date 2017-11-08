CREATE TABLE reimari_turvalaiteryhma (
  tunnus   INTEGER PRIMARY KEY,
  nimi          TEXT,
  kuvaus          TEXT,
  turvalaitteet INTEGER []
);

CREATE UNIQUE INDEX reimari_turvalaiteryhma_tunnus
  ON reimari_turvalaiteryhma (tunnus);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('ptj', 'turvalaiteryhmien-haku');