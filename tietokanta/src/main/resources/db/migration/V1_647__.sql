CREATE TABLE reimari_turvalaiteryhma (
  tunnus   INTEGER PRIMARY KEY,
  nimi          TEXT,
  kuvaus          TEXT,
  turvalaitteet INTEGER []
);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('reimari', 'hae-turvalaiteryhmat');

INSERT INTO reimari_meta (integraatio, enimmaishakuvali, aikakursori)
VALUES (
  (SELECT id FROM integraatio WHERE jarjestelma = 'reimari' AND nimi = 'hae-turvalaiteryhmat'),
  '10 years',
  '2007-01-01T12:12:12Z');
