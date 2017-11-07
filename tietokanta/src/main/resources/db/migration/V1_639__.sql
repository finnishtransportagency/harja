CREATE TABLE reimari_turvalaiteryhma (
  id            SERIAL PRIMARY KEY,
  ryhmanumero   INTEGER,
  nimi          TEXT,
  turvalaitteet INTEGER []
);

CREATE UNIQUE INDEX reimari_turvalaiteryhma_ryhmanumero
  ON reimari_turvalaiteryhma (ryhmanumero);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('ptj', 'turvalaiteryhmien-haku');
